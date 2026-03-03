package com.weiluo.marketalert.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiluo.marketalert.config.AppProperties;
import com.weiluo.marketalert.model.MarketTrade;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.net.URI;
import java.util.List;

@Service
public class StockDataIngestionService {

    private static final Logger log = LoggerFactory.getLogger(StockDataIngestionService.class);

    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final Sinks.Many<MarketTrade> tradeSink;

    public StockDataIngestionService(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.tradeSink = Sinks.many().multicast().onBackpressureBuffer();
    }

    @PostConstruct
    public void startIngestion() {
        if (properties.finnhub() == null || properties.finnhub().apiKey() == null
                || properties.finnhub().apiKey().isBlank()) {
            log.warn("Finnhub API key not configured. WebSocket connection skipped.");
            return;
        }

        ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();
        URI uri = URI.create("wss://ws.finnhub.io?token=" + properties.finnhub().apiKey());

        client.execute(uri, session -> {
            log.info("Connected to Finnhub WebSocket!");

            // Send subscription messages
            Flux<WebSocketMessage> subscriptionMessages = Flux.fromIterable(properties.symbols())
                    .map(symbol -> "{\"type\":\"subscribe\",\"symbol\":\"" + symbol + "\"}").map(session::textMessage);

            return session.send(subscriptionMessages)
                    .thenMany(session.receive().map(WebSocketMessage::getPayloadAsText).doOnNext(this::handleMessage))
                    .then();
        }).subscribe(null, error -> log.error("WebSocket Error: ", error),
                () -> log.info("WebSocket connection closed"));
    }

    private void handleMessage(String payload) {
        try {
            FinnhubMessage message = objectMapper.readValue(payload, FinnhubMessage.class);
            if ("trade".equals(message.type()) && message.data() != null) {
                for (FinnhubTrade t : message.data()) {
                    MarketTrade trade = new MarketTrade(t.s(), t.p(), t.t());
                    log.debug("Received Trade: {}", trade);
                    tradeSink.tryEmitNext(trade);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse Finnhub message: {}", e.getMessage());
        }
    }

    public Flux<MarketTrade> getTradeStream() {
        return tradeSink.asFlux();
    }

    public void injectTrade(MarketTrade trade) {
        tradeSink.tryEmitNext(trade);
    }

    public record FinnhubMessage(String type, List<FinnhubTrade> data) {
    }

    public record FinnhubTrade(double p, String s, long t, double v) {
    }
}
