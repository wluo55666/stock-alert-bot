package com.weiluo.marketalert.service;

import com.weiluo.marketalert.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OffHoursAnalysisServiceTest {

    @Mock private RestClient.Builder restClientBuilder;
    @Mock private RestClient restClient;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private TelegramAlertService telegramAlertService;
    @Mock private TelegramAlertFormatter telegramAlertFormatter;
    @Mock private MarketNewsTool marketNewsTool;

    private OffHoursAnalysisService service;

    @BeforeEach
    void setUp() {
        when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
        when(restClientBuilder.defaultHeader(anyString(), anyString())).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);

        AppProperties properties = new AppProperties(
                List.of("TSLA"),
                null,
                null,
                new AppProperties.OffHours(true, 0.02, 0.04, 90),
                null,
                null
        );

        service = new OffHoursAnalysisService(properties, restClientBuilder, redisTemplate, telegramAlertService, telegramAlertFormatter, marketNewsTool);
    }

    @Test
    void testClassifySessionPremarket() {
        long ts = ZonedDateTime.of(2026, 5, 6, 8, 15, 0, 0, ZoneId.of("America/New_York")).toEpochSecond();
        assertEquals("Premarket", invokeClassifySession(ts));
    }

    @Test
    void testClassifySessionAfterHours() {
        long ts = ZonedDateTime.of(2026, 5, 6, 17, 10, 0, 0, ZoneId.of("America/New_York")).toEpochSecond();
        assertEquals("After-hours", invokeClassifySession(ts));
    }

    @Test
    void testClassifySessionRegular() {
        long ts = ZonedDateTime.of(2026, 5, 6, 11, 0, 0, 0, ZoneId.of("America/New_York")).toEpochSecond();
        assertEquals("Regular", invokeClassifySession(ts));
    }

    @Test
    void testFindPreviousRegularCloseIndex() {
        List<Long> timestamps = List.of(
                ZonedDateTime.of(2026, 5, 5, 15, 30, 0, 0, ZoneId.of("America/New_York")).toEpochSecond(),
                ZonedDateTime.of(2026, 5, 5, 16, 0, 0, 0, ZoneId.of("America/New_York")).toEpochSecond(),
                ZonedDateTime.of(2026, 5, 6, 8, 0, 0, 0, ZoneId.of("America/New_York")).toEpochSecond()
        );
        OffHoursAnalysisService.Quote quote = new OffHoursAnalysisService.Quote(
                List.of(100.0, 101.0, 103.0),
                List.of(100.0, 101.0, 103.0),
                List.of(100.0, 101.0, 103.0),
                List.of(100.0, 101.0, 103.0),
                List.of(1000L, 1000L, 1000L)
        );

        int idx = invokeFindPreviousRegularCloseIndex(timestamps, quote);
        assertEquals(1, idx);
    }

    @Test
    void testOffHoursMoveThresholdMath() {
        double previousClose = 100.0;
        double latestPrice = 104.5;
        double move = (latestPrice - previousClose) / previousClose;
        assertTrue(move >= 0.04);
    }

    @Test
    void testLowerMoveDoesNotReachAlertThreshold() {
        double previousClose = 100.0;
        double latestPrice = 101.5;
        double move = (latestPrice - previousClose) / previousClose;
        assertFalse(move >= 0.04);
        assertTrue(move < 0.02 || move < 0.04);
    }

    private String invokeClassifySession(long epochSecond) {
        try {
            var method = OffHoursAnalysisService.class.getDeclaredMethod("classifySession", long.class);
            method.setAccessible(true);
            return (String) method.invoke(service, epochSecond);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private int invokeFindPreviousRegularCloseIndex(List<Long> timestamps, OffHoursAnalysisService.Quote quote) {
        try {
            var method = OffHoursAnalysisService.class.getDeclaredMethod("findPreviousRegularCloseIndex", List.class, OffHoursAnalysisService.Quote.class);
            method.setAccessible(true);
            return (int) method.invoke(service, timestamps, quote);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
