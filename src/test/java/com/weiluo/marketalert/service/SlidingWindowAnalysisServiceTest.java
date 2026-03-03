package com.weiluo.marketalert.service;

import com.weiluo.marketalert.config.AppProperties;
import com.weiluo.marketalert.model.MarketTrade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.data.redis.core.ReactiveZSetOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlidingWindowAnalysisServiceTest {

    @Mock
    private StockDataIngestionService ingestionService;
    @Mock
    private ReactiveStringRedisTemplate redisTemplate;
    @Mock
    private TelegramAlertService telegramAlertService;

    @Mock
    private ReactiveZSetOperations<String, String> zSetOps;
    @Mock
    private ReactiveValueOperations<String, String> valueOps;

    private AppProperties properties;
    private SlidingWindowAnalysisService analysisService;

    @BeforeEach
    void setUp() {
        properties = new AppProperties(
                List.of("AAPL"),
                new AppProperties.SlidingWindow(300, 0.05), // 5% threshold
                null,
                null);

        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);

        // Mock redis operations so they don't throw NPEs when chained
        when(zSetOps.add(anyString(), anyString(), anyDouble())).thenReturn(Mono.just(true));
        when(zSetOps.removeRangeByScore(anyString(), any(Range.class))).thenReturn(Mono.just(1L));

        analysisService = new SlidingWindowAnalysisService(ingestionService, redisTemplate, telegramAlertService,
                properties);
    }

    @Test
    void testNoAlertOnNormalFluctuation() {
        // Setup a window where price only fluctuated 2% (100 -> 102), threshold is 5%
        when(zSetOps.range(anyString(), any(Range.class)))
                .thenReturn(Flux.just("100.0:1000", "101.0:2000", "102.0:3000"));

        MarketTrade newTrade = new MarketTrade("AAPL", 102.0, 3000L);
        when(ingestionService.getTradeStream()).thenReturn(Flux.just(newTrade));

        analysisService.setupAnalysis(); // triggers stream processing

        // Verify Telegram was NOT called
        verify(telegramAlertService, never()).sendAlert(anyString());
    }

    @Test
    void testAlertOnSpike() {
        // Setup a window where price spiked 10% (100 -> 110), threshold is 5%
        when(zSetOps.range(anyString(), any(Range.class)))
                .thenReturn(Flux.just("100.0:1000", "105.0:2000", "110.0:3000"));

        // Mock deduplication to allow the alert
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        when(telegramAlertService.sendAlert(anyString())).thenReturn(Mono.empty());

        MarketTrade newTrade = new MarketTrade("AAPL", 110.0, 3000L);
        when(ingestionService.getTradeStream()).thenReturn(Flux.just(newTrade));

        analysisService.setupAnalysis();

        // Capture the message sent to Telegram
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramAlertService, times(1)).sendAlert(messageCaptor.capture());

        String sentMessage = messageCaptor.getValue();
        assert (sentMessage.contains("SPIKE"));
        assert (sentMessage.contains("10.00%"));
    }

    @Test
    void testAlertOnDrop() {
        // Setup a window where price dropped 10% (100 -> 90), threshold is 5%
        when(zSetOps.range(anyString(), any(Range.class)))
                .thenReturn(Flux.just("100.0:1000", "95.0:2000", "90.0:3000"));

        // Mock deduplication to allow the alert
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        when(telegramAlertService.sendAlert(anyString())).thenReturn(Mono.empty());

        MarketTrade newTrade = new MarketTrade("AAPL", 90.0, 3000L);
        when(ingestionService.getTradeStream()).thenReturn(Flux.just(newTrade));

        analysisService.setupAnalysis();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramAlertService, times(1)).sendAlert(messageCaptor.capture());

        String sentMessage = messageCaptor.getValue();
        // (100 - 90) / 90 = 10 / 90 = 11.11%
        assert (sentMessage.contains("DROP"));
        assert (sentMessage.contains("11.11%"));
    }

    @Test
    void testDeduplicationPreventsSpam() {
        // Same 10% spike
        when(zSetOps.range(anyString(), any(Range.class))).thenReturn(Flux.just("100.0:1000", "110.0:3000"));

        // Mock deduplication to DENY the alert (setIfAbsent returns false meaning it
        // was already locked)
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(false));

        MarketTrade newTrade = new MarketTrade("AAPL", 110.0, 3000L);
        when(ingestionService.getTradeStream()).thenReturn(Flux.just(newTrade));

        analysisService.setupAnalysis();

        // Verify Telegram was NOT called because deduplicator stopped it
        verify(telegramAlertService, never()).sendAlert(anyString());
    }
}
