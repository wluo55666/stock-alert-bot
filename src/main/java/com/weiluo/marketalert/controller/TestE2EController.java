package com.weiluo.marketalert.controller;

import com.weiluo.marketalert.model.SymbolBar;
import com.weiluo.marketalert.service.StockDataIngestionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ta4j.core.BaseBar;
import org.ta4j.core.num.DoubleNum;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

@RestController
@RequestMapping("/api/test")
public class TestE2EController {

    private final StockDataIngestionService ingestionService;

    public TestE2EController(StockDataIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    public record InjectPayload(String symbol, double open, double high, double low, double close, double volume,
            long timestamp) {
    }

    @PostMapping("/inject")
    public Mono<Void> injectBar(@RequestBody InjectPayload payload) {
        Instant endTime = Instant.ofEpochMilli(payload.timestamp());
        Instant beginTime = endTime.minusSeconds(60);

        BaseBar baseBar = new BaseBar(Duration.ofMinutes(1), beginTime, endTime, DoubleNum.valueOf(payload.open()),
                DoubleNum.valueOf(payload.high()), DoubleNum.valueOf(payload.low()), DoubleNum.valueOf(payload.close()),
                DoubleNum.valueOf(payload.volume()), DoubleNum.valueOf(0), 1L);

        SymbolBar bar = new SymbolBar(payload.symbol(), baseBar);
        ingestionService.injectBar(bar);
        return Mono.empty();
    }
}
