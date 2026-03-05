package com.weiluo.marketalert.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiluo.marketalert.config.AppProperties;
import com.weiluo.marketalert.model.MarketTrade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class StockDataIngestionServiceTest {

    @Mock
    private ObjectMapper objectMapper;

    private AppProperties properties;
    private StockDataIngestionService service;

    @BeforeEach
    void setUp() {
        // Create properties without Finnhub API key to test the logic without
        // connecting to Finnhub
        properties = new AppProperties(List.of("AAPL"), new AppProperties.SlidingWindow(300, 0.05), null, null,
                new AppProperties.Finnhub(null));
        service = new StockDataIngestionService(properties, objectMapper);
        service.startIngestion(); // Does nothing but log a warning since apiKey is null
    }

    @Test
    void testInjectTradeAndGetStream() {
        MarketTrade trade1 = new MarketTrade("AAPL", 150.0, 1000L);
        MarketTrade trade2 = new MarketTrade("MSFT", 300.0, 2000L);

        StepVerifier.create(service.getTradeStream()).then(() -> {
            service.injectTrade(trade1);
            service.injectTrade(trade2);
        }).expectNext(trade1).expectNext(trade2).thenCancel().verify();
    }
}
