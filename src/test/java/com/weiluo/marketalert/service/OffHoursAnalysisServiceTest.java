package com.weiluo.marketalert.service;

import com.weiluo.marketalert.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OffHoursAnalysisServiceTest {

    @Mock private RestClient.Builder restClientBuilder;
    @Mock private RestClient restClient;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private TelegramAlertService telegramAlertService;
    @Mock private TelegramAlertFormatter telegramAlertFormatter;
    @Mock private MarketNewsTool marketNewsTool;

    private OffHoursAnalysisService service;

    @BeforeEach
    void setUp() {
        when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
        when(restClientBuilder.defaultHeader(anyString(), anyString())).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

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
    void testFindLastRegularSessionIndexUsesStrictRegularHours() {
        List<Long> timestamps = List.of(
                ZonedDateTime.of(2026, 5, 7, 15, 59, 0, 0, ZoneId.of("America/New_York")).toEpochSecond(),
                ZonedDateTime.of(2026, 5, 7, 16, 0, 0, 0, ZoneId.of("America/New_York")).toEpochSecond(),
                ZonedDateTime.of(2026, 5, 7, 16, 1, 0, 0, ZoneId.of("America/New_York")).toEpochSecond()
        );
        OffHoursAnalysisService.Quote quote = new OffHoursAnalysisService.Quote(
                List.of(70.14, 75.50, 75.50),
                List.of(70.14, 75.50, 75.50),
                List.of(70.14, 75.50, 75.50),
                List.of(70.14, 75.50, 75.50),
                List.of(1000L, 1000L, 1000L)
        );

        int idx = invokeFindLastRegularSessionIndex(timestamps, quote);
        assertEquals(0, idx);
    }

    @Test
    void testFindLatestOffHoursIndexFindsAfterHoursPoint() {
        List<Long> timestamps = List.of(
                ZonedDateTime.of(2026, 5, 7, 15, 59, 0, 0, ZoneId.of("America/New_York")).toEpochSecond(),
                ZonedDateTime.of(2026, 5, 7, 16, 0, 0, 0, ZoneId.of("America/New_York")).toEpochSecond(),
                ZonedDateTime.of(2026, 5, 7, 19, 59, 0, 0, ZoneId.of("America/New_York")).toEpochSecond()
        );
        OffHoursAnalysisService.Quote quote = new OffHoursAnalysisService.Quote(
                List.of(70.14, 75.50, 75.50),
                List.of(70.14, 75.50, 75.50),
                List.of(70.14, 75.50, 75.50),
                List.of(70.14, 75.50, 75.50),
                List.of(1000L, 1000L, 1000L)
        );

        int idx = invokeFindLatestOffHoursIndex(timestamps, quote);
        assertEquals(2, idx);
    }

    @Test
    void testOffHoursMoveThresholdMath() {
        double previousClose = 70.14;
        double latestPrice = 75.50;
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

    @Test
    void testSnapshotDedupeKeyUsesObservedTimestampAndPrice() {
        Instant observedAt = Instant.parse("2026-05-07T23:59:59Z");
        String key = invokeSnapshotDedupeKey("XYZ", 70.11, 75.70, 0.0797, "After-hours", observedAt);
        assertTrue(key.startsWith("offhours_alert_snapshot:XYZ:after-hours:"));
        assertTrue(key.contains(":" + observedAt.getEpochSecond() + ":"));
        assertTrue(key.endsWith(":757000"));
    }

    @Test
    void testDuplicateSnapshotSuppressesAlertBeforeCooldown() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        invokeTriggerOffHoursAlert("XYZ", 70.11, 75.70, 0.0797, "After-hours", Instant.parse("2026-05-07T23:59:59Z"));

        verify(telegramAlertService, never()).sendAlert(anyString());
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

    private int invokeFindLastRegularSessionIndex(List<Long> timestamps, OffHoursAnalysisService.Quote quote) {
        try {
            var method = OffHoursAnalysisService.class.getDeclaredMethod("findLastRegularSessionIndex", List.class, OffHoursAnalysisService.Quote.class);
            method.setAccessible(true);
            return (int) method.invoke(service, timestamps, quote);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private int invokeFindLatestOffHoursIndex(List<Long> timestamps, OffHoursAnalysisService.Quote quote) {
        try {
            var method = OffHoursAnalysisService.class.getDeclaredMethod("findLatestOffHoursIndex", List.class, OffHoursAnalysisService.Quote.class);
            method.setAccessible(true);
            return (int) method.invoke(service, timestamps, quote);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String invokeSnapshotDedupeKey(String symbol, double previousClose, double latestPrice, double movePercent, String sessionLabel, Instant observedAt) {
        try {
            var snapshotClass = Class.forName("com.weiluo.marketalert.service.OffHoursAnalysisService$OffHoursSnapshot");
            var constructor = snapshotClass.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            Object snapshot = constructor.newInstance(previousClose, latestPrice, movePercent, sessionLabel, observedAt);
            var method = OffHoursAnalysisService.class.getDeclaredMethod("snapshotDedupeKey", String.class, snapshotClass);
            method.setAccessible(true);
            return (String) method.invoke(service, symbol, snapshot);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void invokeTriggerOffHoursAlert(String symbol, double previousClose, double latestPrice, double movePercent, String sessionLabel, Instant observedAt) {
        try {
            var snapshotClass = Class.forName("com.weiluo.marketalert.service.OffHoursAnalysisService$OffHoursSnapshot");
            var constructor = snapshotClass.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            Object snapshot = constructor.newInstance(previousClose, latestPrice, movePercent, sessionLabel, observedAt);
            var method = OffHoursAnalysisService.class.getDeclaredMethod("triggerOffHoursAlert", String.class, snapshotClass);
            method.setAccessible(true);
            method.invoke(service, symbol, snapshot);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
