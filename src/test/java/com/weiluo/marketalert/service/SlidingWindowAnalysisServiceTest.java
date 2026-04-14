package com.weiluo.marketalert.service;

import com.weiluo.marketalert.config.AppProperties;
import com.weiluo.marketalert.model.SymbolBar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.ta4j.core.BaseBar;
import org.ta4j.core.num.DoubleNum;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlidingWindowAnalysisServiceTest {
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private TelegramAlertService telegramAlertService;
    @Mock private TelegramAlertFormatter telegramAlertFormatter;
    @Mock private ZSetOperations<String, String> zSetOps;
    @Mock private ValueOperations<String, String> valueOps;
    private AppProperties properties;
    private SlidingWindowAnalysisService analysisService;

    @BeforeEach
    void setUp() {
        properties = new AppProperties(List.of("AAPL"), new AppProperties.SlidingWindow(300, 0.02, 2, 60, 0.035, 3), null, null, null);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.add(anyString(), anyString(), anyDouble())).thenReturn(true);
        when(zSetOps.removeRangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(1L);
        analysisService = new SlidingWindowAnalysisService(redisTemplate, telegramAlertService, telegramAlertFormatter, properties);
    }

    private SymbolBar createBar(String symbol, double price, long timestamp) {
        java.time.Instant endTime = java.time.Instant.ofEpochMilli(timestamp);
        java.time.Instant beginTime = endTime.minusSeconds(60);
        BaseBar bar = new BaseBar(Duration.ofMinutes(1), beginTime, endTime, DoubleNum.valueOf(price),
                DoubleNum.valueOf(price), DoubleNum.valueOf(price), DoubleNum.valueOf(price), DoubleNum.valueOf(100),
                DoubleNum.valueOf(0), 1L);
        return new SymbolBar(symbol, bar);
    }

    @Test
    void testNoAlertOnNormalFluctuation() {
        when(zSetOps.range(anyString(), anyLong(), anyLong())).thenReturn(Set.of("100.0:1000", "100.4:2000", "100.8:3000"));
        analysisService.processBar(createBar("AAPL", 100.8, 3000L));
        verify(telegramAlertService, never()).sendAlert(anyString());
    }

    @Test
    void testSuppressWeakBreakoutBelowMinimumScore() {
        when(zSetOps.range(anyString(), anyLong(), anyLong())).thenReturn(Set.of("100.0:1000", "101.2:2000", "102.1:3000"));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        analysisService.processBar(createBar("AAPL", 102.1, 3000L));
        verify(telegramAlertService, never()).sendAlert(anyString());
    }

    @Test
    void testAlertOnStrongDirectionalBreakout() {
        when(zSetOps.range(anyString(), anyLong(), anyLong())).thenReturn(Set.of("100.0:1000", "102.0:2000", "104.5:3000", "106.0:4000"));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(telegramAlertFormatter.formatSlidingWindowAlert(anyString(), anyString(), any(Integer.class), any(StructuredTradingAlert.class)))
                .thenReturn("formatted sliding alert");

        analysisService.processBar(createBar("AAPL", 106.0, 4000L));

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramAlertService, times(1)).sendAlert(messageCaptor.capture());
        String sentMessage = messageCaptor.getValue();
        assert(sentMessage.contains("formatted sliding alert"));
    }
}
