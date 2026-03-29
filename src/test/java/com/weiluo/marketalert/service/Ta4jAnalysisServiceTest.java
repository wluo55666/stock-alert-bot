package com.weiluo.marketalert.service;
import com.weiluo.marketalert.config.AppProperties;
import com.weiluo.marketalert.model.SymbolBar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.ta4j.core.BaseBar;
import java.time.Duration;
import java.util.List;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Ta4jAnalysisServiceTest {
    @Mock private TelegramAlertService telegramAlertService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private SmartTradingAgent smartTradingAgent;
    private AppProperties properties;
    private Ta4jAnalysisService analysisService;

    @BeforeEach
    void setUp() {
        properties = new AppProperties(List.of("AAPL"), null, new AppProperties.Ta4j(900, 14, 12, 26, 9), null, null);
        analysisService = new Ta4jAnalysisService(telegramAlertService, properties, redisTemplate, smartTradingAgent);
    }

    @Test
    void testNoAlertWhenNotEnoughBars() {
        for (int i = 1; i <= 10; i++) {
            analysisService.processBar(createBar("AAPL", 150.0 + i, i));
        }
        verify(telegramAlertService, never()).sendAlert(anyString());
    }

    private SymbolBar createBar(String symbol, double price, int minuteOffset) {
        java.time.Instant endTime = java.time.Instant.now().plus(Duration.ofMinutes(minuteOffset));
        java.time.Instant beginTime = endTime.minus(Duration.ofMinutes(1));
        BaseBar bar = new BaseBar(Duration.ofMinutes(1), beginTime, endTime, org.ta4j.core.num.DoubleNum.valueOf(price),
                org.ta4j.core.num.DoubleNum.valueOf(price), org.ta4j.core.num.DoubleNum.valueOf(price),
                org.ta4j.core.num.DoubleNum.valueOf(price), org.ta4j.core.num.DoubleNum.valueOf(100),
                org.ta4j.core.num.DoubleNum.valueOf(0), 1L);
        return new SymbolBar(symbol, bar);
    }
}
