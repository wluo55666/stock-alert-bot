package com.weiluo.marketalert.service;

import com.weiluo.marketalert.config.AppProperties;
import com.weiluo.marketalert.model.SymbolBar;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.ta4j.core.BaseBar;
import org.ta4j.core.num.DoubleNum;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StockDataIngestionService {
    private static final Logger log = LoggerFactory.getLogger(StockDataIngestionService.class);
    private static final int MAX_FETCH_ATTEMPTS = 3;
    private static final long RETRY_BACKOFF_MS = 750;

    private final AppProperties properties;
    private final RestClient restClient;
    private final ApplicationEventPublisher eventPublisher;
    private final Map<String, Instant> lastPublishedBarEndBySymbol = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastObservedBarEndBySymbol = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastLoggedStaleBarEndBySymbol = new ConcurrentHashMap<>();
    private List<String> validSymbols;

    public StockDataIngestionService(AppProperties properties, RestClient.Builder restClientBuilder, ApplicationEventPublisher eventPublisher) {
        this.properties = properties;
        this.restClient = restClientBuilder.baseUrl("https://query1.finance.yahoo.com/v8/finance/chart")
                .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build();
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    public void init() {
        this.validSymbols = properties.symbols().stream().filter(sym -> !sym.startsWith("TEST_")).toList();
        log.info("Started Yahoo Finance REST Polling (15-min candles) for symbols: {}", validSymbols);
    }

    @Scheduled(fixedRate = 60000)
    public void pollMarketData() {
        if (validSymbols == null || validSymbols.isEmpty()) return;
        for (String symbol : validSymbols) {
            try {
                SymbolBar bar = fetchLatestCandle(symbol);
                if (bar != null && shouldPublish(symbol, bar)) {
                    injectBar(bar);
                }
                Thread.sleep(500);
            } catch (Exception e) {
                log.error("Error in Yahoo Finance polling loop for {}: {}", symbol, e.getMessage());
            }
        }
    }

    private SymbolBar fetchLatestCandle(String symbol) {
        for (int attempt = 1; attempt <= MAX_FETCH_ATTEMPTS; attempt++) {
            try {
                YahooResponse response = restClient.get().uri("/{symbol}?interval=15m&range=5d", symbol).retrieve().body(YahooResponse.class);
                if (response == null || response.chart() == null || response.chart().result() == null || response.chart().result().isEmpty()) {
                    log.debug("No result data for {}.", symbol);
                    return null;
                }
                Result result = response.chart().result().get(0);
                if (result.timestamp() == null || result.timestamp().isEmpty()) return null;
                Quote quote = result.indicators().quote().get(0);
                int lastIdx = result.timestamp().size() - 1;
                while (lastIdx >= 0 && isIncompleteBar(quote, lastIdx)) {
                    lastIdx--;
                }
                if (lastIdx < 0) {
                    log.debug("No completed candle available for {}.", symbol);
                    return null;
                }
                Instant beginTime = Instant.ofEpochSecond(result.timestamp().get(lastIdx));
                Instant endTime = beginTime.plusSeconds(900);
                if (isStaleObservation(symbol, endTime)) {
                    return null;
                }
                BaseBar bar = new BaseBar(Duration.ofMinutes(15), beginTime, endTime,
                        DoubleNum.valueOf(quote.open().get(lastIdx)), DoubleNum.valueOf(quote.high().get(lastIdx)),
                        DoubleNum.valueOf(quote.low().get(lastIdx)), DoubleNum.valueOf(quote.close().get(lastIdx)),
                        DoubleNum.valueOf(quote.volume().get(lastIdx)), DoubleNum.valueOf(0), 1L);
                return new SymbolBar(symbol, bar);
            } catch (ResourceAccessException e) {
                if (attempt < MAX_FETCH_ATTEMPTS) {
                    log.warn("Yahoo fetch transient failure for {} on attempt {}/{}: {}. Retrying...", symbol, attempt, MAX_FETCH_ATTEMPTS, rootMessage(e));
                    sleepBeforeRetry();
                } else {
                    log.warn("Yahoo fetch failed for {} after {}/{} attempts due to network/TLS issue: {}", symbol, attempt, MAX_FETCH_ATTEMPTS, rootMessage(e));
                }
            } catch (Exception e) {
                log.warn("Yahoo response processing failed for {}: {}", symbol, rootMessage(e));
                return null;
            }
        }
        return null;
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_BACKOFF_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() != null ? current.getMessage() : throwable.getMessage();
    }

    public void injectBar(SymbolBar bar) {
        eventPublisher.publishEvent(bar);
    }

    private boolean shouldPublish(String symbol, SymbolBar bar) {
        Instant newEndTime = bar.bar().getEndTime();
        Instant previousEndTime = lastPublishedBarEndBySymbol.putIfAbsent(symbol, newEndTime);
        if (previousEndTime == null) {
            lastLoggedStaleBarEndBySymbol.remove(symbol);
            log.info("Publishing first completed 15M bar for {} ending at {} (close={}, volume={})",
                    symbol,
                    newEndTime,
                    bar.bar().getClosePrice(),
                    bar.bar().getVolume());
            return true;
        }
        if (newEndTime.isAfter(previousEndTime)) {
            lastPublishedBarEndBySymbol.put(symbol, newEndTime);
            lastLoggedStaleBarEndBySymbol.remove(symbol);
            log.info("Publishing new completed 15M bar for {} ending at {} (close={}, volume={})",
                    symbol,
                    newEndTime,
                    bar.bar().getClosePrice(),
                    bar.bar().getVolume());
            return true;
        }
        return false;
    }

    private boolean isStaleObservation(String symbol, Instant endTime) {
        Instant previousObserved = lastObservedBarEndBySymbol.put(symbol, endTime);
        if (previousObserved != null && !endTime.isAfter(previousObserved)) {
            Instant lastLoggedStale = lastLoggedStaleBarEndBySymbol.put(symbol, endTime);
            if (lastLoggedStale == null || !lastLoggedStale.equals(endTime)) {
                log.debug("No newer completed candle for {} since {}", symbol, previousObserved);
            }
            return true;
        }
        lastLoggedStaleBarEndBySymbol.remove(symbol);
        return false;
    }

    private boolean isIncompleteBar(Quote quote, int index) {
        return quote.open().get(index) == null
                || quote.high().get(index) == null
                || quote.low().get(index) == null
                || quote.close().get(index) == null
                || quote.volume().get(index) == null
                || quote.volume().get(index) <= 0;
    }

    public record YahooResponse(Chart chart) {}
    public record Chart(List<Result> result, Object error) {}
    public record Result(List<Long> timestamp, Indicators indicators) {}
    public record Indicators(List<Quote> quote) {}
    public record Quote(List<Double> open, List<Double> high, List<Double> low, List<Double> close, List<Long> volume) {}
}
