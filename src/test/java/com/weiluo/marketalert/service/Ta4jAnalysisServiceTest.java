package com.weiluo.marketalert.service;

import com.weiluo.marketalert.config.AppProperties;
import com.weiluo.marketalert.service.MarketTradeAggregator.SymbolBar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.ta4j.core.BaseBar;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Ta4jAnalysisServiceTest {

    @Mock
    private StockDataIngestionService ingestionService;
    @Mock
    private MarketTradeAggregator aggregator;
    @Mock
    private TelegramAlertService telegramAlertService;
    @Mock
    private ReactiveStringRedisTemplate redisTemplate;
    @Mock
    private ReactiveValueOperations<String, String> valueOps;

    private AppProperties properties;
    private Ta4jAnalysisService analysisService;

    @BeforeEach
    void setUp() {
        properties = new AppProperties(List.of("AAPL"), null, new AppProperties.Ta4j(60, 14, 12, 26, 9), null, null);

        analysisService = new Ta4jAnalysisService(ingestionService, aggregator, telegramAlertService, properties,
                redisTemplate);
    }

    @Test
    void testNoAlertWhenNotEnoughBars() {
        // Create 10 bars (less than MACD long + signal = 26 + 9 = 35 needed)
        Flux<SymbolBar> dummyBars = Flux.range(1, 10).map(i -> createBar("AAPL", 150.0 + i, i));

        when(ingestionService.getTradeStream()).thenReturn(Flux.empty());
        when(aggregator.aggregateToBars(any(), any())).thenReturn(dummyBars);

        analysisService.setupAnalysis();

        verify(telegramAlertService, never()).sendAlert(anyString());
    }

    private SymbolBar createBar(String symbol, double price, int minuteOffset) {
        ZonedDateTime endTime = ZonedDateTime.now(ZoneId.systemDefault()).plusMinutes(minuteOffset);
        BaseBar bar = new BaseBar(Duration.ofMinutes(1), endTime, price, price, price, price, 100);
        return new SymbolBar(symbol, bar);
    }
}
