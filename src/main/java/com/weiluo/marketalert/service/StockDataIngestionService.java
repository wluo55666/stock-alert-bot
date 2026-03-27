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
import java.util.List;

@Service
public class StockDataIngestionService {

    private static final Logger log = LoggerFactory.getLogger(StockDataIngestionService.class);

    private final AppProperties properties;
    private final WebClient webClient;
    private final Sinks.Many<SymbolBar> barSink;

    public StockDataIngestionService(AppProperties properties, WebClient.Builder webClientBuilder) {
        this.properties = properties;
        this.webClient = webClientBuilder
                .baseUrl("https://query1.finance.yahoo.com/v8/finance/chart")
                .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build();
        this.barSink = Sinks.many().multicast().onBackpressureBuffer();
    }

    @PostConstruct
    public void startIngestion() {
        // Filter out test symbols so we don't spam Yahoo with invalid queries
        List<String> validSymbols = properties.symbols().stream()
                .filter(sym -> !sym.startsWith("TEST_"))
                .toList();

        Flux.interval(Duration.ofSeconds(60))
                .flatMap(tick -> Flux.fromIterable(validSymbols)
                        .delayElements(Duration.ofMillis(500))) // Stagger requests to stay under limits
                .flatMap(this::fetchLatestCandle)
                .subscribe(this::injectBar, error -> log.error("Error in Yahoo Finance polling loop: ", error));

        log.info("Started Yahoo Finance REST Polling (15-min candles) for symbols: {}", validSymbols);
    }

    private Flux<SymbolBar> fetchLatestCandle(String symbol) {
        return webClient.get()
                .uri("/{symbol}?interval=15m&range=5d", symbol)
                .retrieve()
                .bodyToMono(YahooResponse.class)
                .flatMapMany(response -> {
                    try {
                        if (response.chart() == null || response.chart().result() == null || response.chart().result().isEmpty()) {
                            log.debug("No result data for {}.", symbol);
                            return Flux.empty();
                        }
                        
                        Result result = response.chart().result().get(0);
                        if (result.timestamp() == null || result.timestamp().isEmpty()) {
                            return Flux.empty();
                        }

                        Quote quote = result.indicators().quote().get(0);
                        
                        // Find the last valid index (sometimes current minute volume/close is still null)
                        int lastIdx = result.timestamp().size() - 1;
                        while (lastIdx >= 0 && (quote.close().get(lastIdx) == null || quote.volume().get(lastIdx) == null)) {
                            lastIdx--;
                        }

                        if (lastIdx < 0) {
                            return Flux.empty();
                        }

                        Instant beginTime = Instant.ofEpochSecond(result.timestamp().get(lastIdx));
                        Instant endTime = beginTime.plusSeconds(900); // 15-minute bar

                        BaseBar bar = new BaseBar(Duration.ofMinutes(15), beginTime, endTime,
                                DoubleNum.valueOf(quote.open().get(lastIdx)),
                                DoubleNum.valueOf(quote.high().get(lastIdx)),
                                DoubleNum.valueOf(quote.low().get(lastIdx)),
                                DoubleNum.valueOf(quote.close().get(lastIdx)),
                                DoubleNum.valueOf(quote.volume().get(lastIdx)),
                                DoubleNum.valueOf(0), // Amount (not provided)
                                1L // Trade count approximation
                        );

                        log.debug("Built 15M Bar for {}: {}", symbol, bar);
                        return Flux.just(new SymbolBar(symbol, bar));
                    } catch (Exception e) {
                        log.warn("Failed to parse Yahoo Finance response for {}: {}", symbol, e.getMessage());
                        return Flux.empty();
                    }
                })
                .onErrorResume(e -> {
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

    // Yahoo Finance JSON Models
    public record YahooResponse(Chart chart) {}
    public record Chart(List<Result> result, Object error) {}
    public record Result(List<Long> timestamp, Indicators indicators) {}
    public record Indicators(List<Quote> quote) {}
    public record Quote(List<Double> open, List<Double> high, List<Double> low, List<Double> close, List<Long> volume) {}
}