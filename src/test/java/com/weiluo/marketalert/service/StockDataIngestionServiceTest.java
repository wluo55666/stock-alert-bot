package com.weiluo.marketalert.service;

import com.weiluo.marketalert.config.AppProperties;
import com.weiluo.marketalert.model.SymbolBar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.client.RestClient;
import org.ta4j.core.BaseBar;
import org.ta4j.core.num.DoubleNum;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockDataIngestionServiceTest {
    @Mock private RestClient.Builder restClientBuilder;
    @Mock private RestClient restClient;
    @Mock private ApplicationEventPublisher eventPublisher;
    private AppProperties properties;
    private StockDataIngestionService service;

    @BeforeEach
    void setUp() {
        when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
        when(restClientBuilder.defaultHeader(anyString(), anyString())).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);
        properties = new AppProperties(List.of("AAPL"), new AppProperties.SlidingWindow(300, 0.05, 2, 60, 0.08, 3), null, null, null);
        service = new StockDataIngestionService(properties, restClientBuilder, eventPublisher);
        service.init();
    }

    @Test
    void testInjectBar() {
        Instant now = Instant.now();
        BaseBar bar1 = new BaseBar(Duration.ofMinutes(1), now.minusSeconds(60), now, DoubleNum.valueOf(150),
                DoubleNum.valueOf(155), DoubleNum.valueOf(145), DoubleNum.valueOf(150), DoubleNum.valueOf(100),
                DoubleNum.valueOf(0), 10L);
        SymbolBar symbolBar1 = new SymbolBar("AAPL", bar1);

        service.injectBar(symbolBar1);

        ArgumentCaptor<SymbolBar> captor = ArgumentCaptor.forClass(SymbolBar.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals("AAPL", captor.getValue().symbol());
    }

    @Test
    void testDuplicateBarSnapshotsShouldOnlyPublishOnce() {
        Instant endTime = Instant.now();
        SymbolBar first = createBar("AAPL", 150.0, endTime);
        SymbolBar duplicate = createBar("AAPL", 151.0, endTime);
        SymbolBar next = createBar("AAPL", 152.0, endTime.plus(Duration.ofMinutes(15)));

        if (invokeShouldPublish("AAPL", first)) {
            service.injectBar(first);
        }
        if (invokeShouldPublish("AAPL", duplicate)) {
            service.injectBar(duplicate);
        }
        if (invokeShouldPublish("AAPL", next)) {
            service.injectBar(next);
        }

        verify(eventPublisher, times(2)).publishEvent(org.mockito.ArgumentMatchers.any(SymbolBar.class));
    }

    @Test
    void testStaleObservationShouldBeDetected() {
        Instant endTime = Instant.now();
        assertFalse(invokeIsStaleObservation("AAPL", endTime));
        assertFalse(invokeIsStaleObservation("AAPL", endTime.plus(Duration.ofMinutes(15))));
        boolean stale = invokeIsStaleObservation("AAPL", endTime.plus(Duration.ofMinutes(15)));
        assertEquals(true, stale);
    }

    private boolean invokeShouldPublish(String symbol, SymbolBar bar) {
        try {
            var method = StockDataIngestionService.class.getDeclaredMethod("shouldPublish", String.class, SymbolBar.class);
            method.setAccessible(true);
            return (boolean) method.invoke(service, symbol, bar);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean invokeIsStaleObservation(String symbol, Instant endTime) {
        try {
            var method = StockDataIngestionService.class.getDeclaredMethod("isStaleObservation", String.class, Instant.class);
            method.setAccessible(true);
            return (boolean) method.invoke(service, symbol, endTime);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private SymbolBar createBar(String symbol, double price, Instant endTime) {
        Instant beginTime = endTime.minus(Duration.ofMinutes(15));
        BaseBar bar = new BaseBar(Duration.ofMinutes(15), beginTime, endTime, DoubleNum.valueOf(price),
                DoubleNum.valueOf(price), DoubleNum.valueOf(price), DoubleNum.valueOf(price), DoubleNum.valueOf(1000),
                DoubleNum.valueOf(0), 1L);
        return new SymbolBar(symbol, bar);
    }
}
