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
import static org.mockito.ArgumentMatchers.anyString;
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
        properties = new AppProperties(List.of("AAPL"), new AppProperties.SlidingWindow(300, 0.05), null, null);
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
}
