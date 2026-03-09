package com.weiluo.marketalert.controller;

import com.weiluo.marketalert.model.SymbolBar;
import com.weiluo.marketalert.service.StockDataIngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import org.ta4j.core.BaseBar;
import org.ta4j.core.num.DoubleNum;
import java.time.Duration;
import java.time.Instant;

@WebFluxTest(TestE2EController.class)
class TestE2EControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private StockDataIngestionService ingestionService;

    @Test
    void testInjectBarEndpoint() {
        Instant now = Instant.now();
        TestE2EController.InjectPayload payload = new TestE2EController.InjectPayload("NVDA", 100.0, 105.0, 95.0, 102.0,
                1000.0, now.toEpochMilli());

        webTestClient.post().uri("/api/test/inject").contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(payload), TestE2EController.InjectPayload.class).exchange().expectStatus().isOk()
                .expectBody().isEmpty();

        // Verify that the controller called the service's injectBar method
        verify(ingestionService).injectBar(any(SymbolBar.class));
    }
}
