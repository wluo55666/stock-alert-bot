package com.weiluo.marketalert.service;

import com.weiluo.marketalert.config.AppProperties;
import com.weiluo.marketalert.model.SymbolBar;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.ta4j.core.BaseBar;
import org.ta4j.core.num.DoubleNum;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Service
public class StockDataIngestionService {

    private static final Logger log = LoggerFactory.getLogger(StockDataIngestionService.class);

    private final AppProperties properties;
    private final WebClient webClient;
    private final Sinks.Many<SymbolBar> barSink;

    public StockDataIngestionService(AppProperties properties, WebClient.Builder webClientBuilder) {
        this.properties = properties;
        this.webClient = webClientBuilder.baseUrl("https://finnhub.io/api/v1").build();
        this.barSink = Sinks.many().multicast().onBackpressureBuffer();
    }

    @PostConstruct
    public void startIngestion() {
        if (properties.finnhub() == null || properties.finnhub().apiKey() == null
                || properties.finnhub().apiKey().isBlank()) {
            log.warn("Finnhub API key not configured. REST polling skipped.");
            return;
        }

        String apiKey = properties.finnhub().apiKey();

        Flux.interval(Duration.ofSeconds(60))
                .flatMap(tick -> Flux.fromIterable(properties.symbols()).delayElements(Duration.ofMillis(200))) // Stagger
                                                                                                                // requests
                                                                                                                // to
                                                                                                                // stay
                                                                                                                // under
                                                                                                                // 60/min
                                                                                                                // burst
                                                                                                                // limit
                .flatMap(symbol -> fetchLatestCandle(symbol, apiKey))
                .subscribe(this::injectBar, error -> log.error("Error in Finnhub polling loop: ", error));

        log.info("Started Finnhub REST Polling (1-min candles) for symbols: {}", properties.symbols());
    }

    private Flux<SymbolBar> fetchLatestCandle(String symbol, String apiKey) {
        long endTimestamp = Instant.now().getEpochSecond();
        long startTimestamp = endTimestamp - 60; // Fetch the last 1 minute

        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/stock/candle").queryParam("symbol", symbol)
                        .queryParam("resolution", "1").queryParam("from", startTimestamp).queryParam("to", endTimestamp)
                        .queryParam("token", apiKey).build())
                .retrieve().bodyToMono(FinnhubCandleResponse.class).flatMapMany(response -> {
                    if ("ok".equals(response.s()) && response.t() != null && !response.t().isEmpty()) {
                        // The REST API might return multiple candles if time ranges overlap slightly.
                        // We safely grab the last one.
                        int lastIdx = response.t().size() - 1;

                        Instant endTime = Instant.ofEpochSecond(response.t().get(lastIdx));
                        Instant beginTime = endTime.minusSeconds(60);

                        BaseBar bar = new BaseBar(Duration.ofMinutes(1), beginTime, endTime,
                                DoubleNum.valueOf(response.o().get(lastIdx)),
                                DoubleNum.valueOf(response.h().get(lastIdx)),
                                DoubleNum.valueOf(response.l().get(lastIdx)),
                                DoubleNum.valueOf(response.c().get(lastIdx)),
                                DoubleNum.valueOf(response.v().get(lastIdx)), DoubleNum.valueOf(0), // Amount (not
                                                                                                    // provided)
                                1L // Trade count approximation
                        );

                        log.debug("Built 1M Bar for {}: {}", symbol, bar);
                        return Flux.just(new SymbolBar(symbol, bar));
                    } else if ("no_data".equals(response.s())) {
                        log.debug("No data for {} in the last minute.", symbol);
                    } else {
                        log.warn("Unexpected response from Finnhub for {}: {}", symbol, response.s());
                    }
                    return Flux.empty();
                }).onErrorResume(e -> {
                    log.error("Failed to fetch candle for {}: {}", symbol, e.getMessage());
                    return Flux.empty();
                });
    }

    public Flux<SymbolBar> getBarStream() {
        return barSink.asFlux();
    }

    public void injectBar(SymbolBar bar) {
        barSink.tryEmitNext(bar);
    }

    public record FinnhubCandleResponse(List<Double> c, // Close
            List<Double> h, // High
            List<Double> l, // Low
            List<Double> o, // Open
            String s, // Status
            List<Long> t, // Timestamp
            List<Long> v // Volume
    ) {
    }
}
