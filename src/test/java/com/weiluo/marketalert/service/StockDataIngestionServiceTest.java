package com.weiluo.marketalert.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiluo.marketalert.config.AppProperties;
import com.weiluo.marketalert.model.SymbolBar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.ta4j.core.BaseBar;
import org.ta4j.core.num.DoubleNum;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockDataIngestionServiceTest {

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    private AppProperties properties;
    private StockDataIngestionService service;

    @BeforeEach
    void setUp() {
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);

        // Create properties without Finnhub API key to test the logic without
        // connecting to Finnhub
        properties = new AppProperties(List.of("AAPL"), new AppProperties.SlidingWindow(300, 0.05), null, null,
                new AppProperties.Finnhub(null));
        service = new StockDataIngestionService(properties, webClientBuilder);
        service.startIngestion(); // Does nothing but log a warning since apiKey is null
    }

    @Test
    void testInjectBarAndGetStream() {
        Instant now = Instant.now();
        BaseBar bar1 = new BaseBar(Duration.ofMinutes(1), now.minusSeconds(60), now, DoubleNum.valueOf(150),
                DoubleNum.valueOf(155), DoubleNum.valueOf(145), DoubleNum.valueOf(150), DoubleNum.valueOf(100),
                DoubleNum.valueOf(0), 10L);
        SymbolBar symbolBar1 = new SymbolBar("AAPL", bar1);

        BaseBar bar2 = new BaseBar(Duration.ofMinutes(1), now.minusSeconds(60), now, DoubleNum.valueOf(300),
                DoubleNum.valueOf(305), DoubleNum.valueOf(295), DoubleNum.valueOf(300), DoubleNum.valueOf(100),
                DoubleNum.valueOf(0), 10L);
        SymbolBar symbolBar2 = new SymbolBar("MSFT", bar2);

        StepVerifier.create(service.getBarStream()).then(() -> {
            service.injectBar(symbolBar1);
            service.injectBar(symbolBar2);
        }).expectNext(symbolBar1).expectNext(symbolBar2).thenCancel().verify();
    }
}
