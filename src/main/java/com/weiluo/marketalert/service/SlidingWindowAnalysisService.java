package com.weiluo.marketalert.service;

import com.weiluo.marketalert.config.AppProperties;
import com.weiluo.marketalert.model.MarketTrade;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class SlidingWindowAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(SlidingWindowAnalysisService.class);

    private final StockDataIngestionService ingestionService;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final TelegramAlertService telegramAlertService;
    private final AppProperties properties;

    public SlidingWindowAnalysisService(StockDataIngestionService ingestionService,
            ReactiveStringRedisTemplate redisTemplate, TelegramAlertService telegramAlertService,
            AppProperties properties) {
        this.ingestionService = ingestionService;
        this.redisTemplate = redisTemplate;
        this.telegramAlertService = telegramAlertService;
        this.properties = properties;
    }

    @PostConstruct
    public void setupAnalysis() {
        if (properties.slidingWindow() == null) {
            log.warn("Sliding window config missing. Analysis disabled.");
            return;
        }

        ingestionService.getTradeStream().flatMap(this::processTrade).subscribe(null,
                error -> log.error("Error in sliding window analysis", error));
    }

    private Mono<Void> processTrade(MarketTrade trade) {
        String key = "window:" + trade.symbol();
        long now = trade.timestamp();
        long windowStart = now - (properties.slidingWindow().durationSeconds() * 1000L);

        // Add to sorted set: key, value (price string), score (timestamp)
        return redisTemplate.opsForZSet().add(key, String.valueOf(trade.price()) + ":" + now, now)
                // Remove older entries
                .then(redisTemplate.opsForZSet().removeRangeByScore(key, Range.closed(0.0, (double) windowStart - 1)))
                // Fetch the remaining window
                .thenMany(redisTemplate.opsForZSet().range(key, Range.unbounded())).collectList()
                .flatMap(items -> analyzeWindow(trade.symbol(), items, trade.price())).then();
    }

    private Mono<Void> analyzeWindow(String symbol, java.util.List<String> window, double currentPrice) {
        if (window.isEmpty())
            return Mono.empty();

        double minPrice = Double.MAX_VALUE;
        double maxPrice = Double.MIN_VALUE;

        for (String item : window) {
            // value is price:timestamp
            double price = Double.parseDouble(item.split(":")[0]);
            minPrice = Math.min(minPrice, price);
            maxPrice = Math.max(maxPrice, price);
        }

        double threshold = properties.slidingWindow().thresholdPercent();
        double percentChange = (maxPrice - minPrice) / minPrice;

        if (percentChange >= threshold) {
            String direction = currentPrice == maxPrice ? "SPIKE" : (currentPrice == minPrice ? "DROP" : "FLUCTUATION");
            return triggerAlert(symbol, minPrice, maxPrice, percentChange, direction);
        }

        return Mono.empty();
    }

    private Mono<Void> triggerAlert(String symbol, double minPrice, double maxPrice, double percentChange,
            String direction) {
        String deduplicationKey = "alert:" + symbol;
        Duration lockDuration = Duration.ofMinutes(1); // Do not alert more than once a minute for the same symbol

        return redisTemplate.opsForValue().setIfAbsent(deduplicationKey, "locked", lockDuration).flatMap(locked -> {
            if (Boolean.TRUE.equals(locked)) {
                String message = String.format(
                        "🚨 <b>%s Price %s!</b>\n\n" + "🔹 <b>Change:</b> %.2f%%\n" + "🔹 <b>Min Price:</b> $%.2f\n"
                                + "🔹 <b>Max Price:</b> $%.2f\n" + "💡 <i>Sudden price movement detected.</i>",
                        symbol, direction, percentChange * 100, minPrice, maxPrice);
                log.info("Triggering alert for {}: {} {}", symbol, direction, percentChange);
                return telegramAlertService.sendAlert(message);
            } else {
                log.debug("Alert for {} recently sent. Deduplicating.", symbol);
                return Mono.empty();
            }
        });
    }
}
