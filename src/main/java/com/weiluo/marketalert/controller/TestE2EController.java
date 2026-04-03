package com.weiluo.marketalert.controller;

import com.weiluo.marketalert.model.SymbolBar;
import com.weiluo.marketalert.service.SmartTradingAgent;
import com.weiluo.marketalert.service.StockDataIngestionService;
import com.weiluo.marketalert.service.TelegramAlertService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ta4j.core.BaseBar;
import org.ta4j.core.num.DoubleNum;

import java.time.Duration;
import java.time.Instant;

@RestController
@RequestMapping("/api/test")
@ConditionalOnProperty(name = "app.test-endpoints-enabled", havingValue = "true")
public class TestE2EController {
    private final StockDataIngestionService ingestionService;
    private final SmartTradingAgent smartTradingAgent;
    private final TelegramAlertService telegramAlertService;

    public TestE2EController(StockDataIngestionService ingestionService, SmartTradingAgent smartTradingAgent, TelegramAlertService telegramAlertService) {
        this.ingestionService = ingestionService;
        this.smartTradingAgent = smartTradingAgent;
        this.telegramAlertService = telegramAlertService;
    }

    public record InjectPayload(String symbol, double open, double high, double low, double close, double volume, long timestamp) {}
    public record AlertPayload(String symbol, String signal, double price, double rsi, int confirmationBars, int score, String technicalExplanation) {}

    @PostMapping("/inject")
    public ResponseEntity<Void> injectBar(@RequestBody InjectPayload payload) {
        Instant endTime = Instant.ofEpochMilli(payload.timestamp());
        Instant beginTime = endTime.minusSeconds(60);
        BaseBar baseBar = new BaseBar(Duration.ofMinutes(1), beginTime, endTime, DoubleNum.valueOf(payload.open()),
                DoubleNum.valueOf(payload.high()), DoubleNum.valueOf(payload.low()), DoubleNum.valueOf(payload.close()),
                DoubleNum.valueOf(payload.volume()), DoubleNum.valueOf(0), 1L);
        SymbolBar bar = new SymbolBar(payload.symbol(), baseBar);
        ingestionService.injectBar(bar);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/alert")
    public ResponseEntity<Void> triggerAlert(@RequestBody AlertPayload payload) {
        String message = smartTradingAgent.synthesizeAlert(
                payload.symbol(),
                payload.signal(),
                payload.price(),
                payload.rsi(),
                payload.confirmationBars(),
                payload.score(),
                payload.technicalExplanation()
        );
        telegramAlertService.sendAlert(message);
        return ResponseEntity.ok().build();
    }
}
