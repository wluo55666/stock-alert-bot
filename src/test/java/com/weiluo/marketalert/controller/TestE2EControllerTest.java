package com.weiluo.marketalert.controller;

import com.weiluo.marketalert.model.MarketTrade;
import com.weiluo.marketalert.service.StockDataIngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.Mockito.verify;

@WebFluxTest(TestE2EController.class)
class TestE2EControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private StockDataIngestionService ingestionService;

    @Test
    void testInjectTradeEndpoint() {
        MarketTrade trade = new MarketTrade("NVDA", 120.5, 123456789L);

        webTestClient.post().uri("/api/test/inject").contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(trade), MarketTrade.class).exchange().expectStatus().isOk().expectBody().isEmpty();

        // Verify that the controller called the service's injectTrade method
        verify(ingestionService).injectTrade(trade);
    }
}
