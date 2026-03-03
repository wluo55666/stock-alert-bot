package com.weiluo.marketalert.controller;

import com.weiluo.marketalert.model.MarketTrade;
import com.weiluo.marketalert.service.StockDataIngestionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/test")
public class TestE2EController {

    private final StockDataIngestionService ingestionService;

    public TestE2EController(StockDataIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/inject")
    public Mono<Void> injectTrade(@RequestBody MarketTrade trade) {
        ingestionService.injectTrade(trade);
        return Mono.empty();
    }
}
