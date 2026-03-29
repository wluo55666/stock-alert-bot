package com.weiluo.marketalert.service;
import com.weiluo.marketalert.config.AppProperties;
import com.weiluo.marketalert.model.SymbolBar;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class SlidingWindowAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(SlidingWindowAnalysisService.class);
    private final StringRedisTemplate redisTemplate;
    private final TelegramAlertService telegramAlertService;
    private final AppProperties properties;

    public SlidingWindowAnalysisService(StringRedisTemplate redisTemplate, TelegramAlertService telegramAlertService, AppProperties properties) {
        this.redisTemplate = redisTemplate;
        this.telegramAlertService = telegramAlertService;
        this.properties = properties;
    }

    @PostConstruct
    public void setupAnalysis() {
        if (properties.slidingWindow() == null) log.warn("Sliding window config missing. Analysis disabled.");
    }

    @Async
    @EventListener
    public void processBar(SymbolBar symbolBar) {
        if (properties.slidingWindow() == null) return;
        String key = "window:" + symbolBar.symbol();
        long now = symbolBar.bar().getEndTime().toEpochMilli();
        double price = symbolBar.bar().getClosePrice().doubleValue();
        long windowStart = now - (properties.slidingWindow().durationSeconds() * 1000L);
        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
        zSetOps.add(key, String.valueOf(price) + ":" + now, now);
        zSetOps.removeRangeByScore(key, 0.0, windowStart - 1);
        Set<String> items = zSetOps.range(key, 0, -1);
        if (items != null) analyzeWindow(symbolBar.symbol(), new ArrayList<>(items), price);
    }

    private void analyzeWindow(String symbol, List<String> window, double currentPrice) {
        if (window.isEmpty()) return;
        double minPrice = Double.MAX_VALUE;
        double maxPrice = Double.MIN_VALUE;
        for (String item : window) {
            double price = Double.parseDouble(item.split(":")[0]);
            minPrice = Math.min(minPrice, price);
            maxPrice = Math.max(maxPrice, price);
        }
        double threshold = properties.slidingWindow().thresholdPercent();
        double percentChange = (maxPrice - minPrice) / minPrice;
        if (percentChange >= threshold) {
            String direction = currentPrice == maxPrice ? "SPIKE" : (currentPrice == minPrice ? "DROP" : "FLUCTUATION");
            triggerAlert(symbol, minPrice, maxPrice, percentChange, direction);
        }
    }

    private void triggerAlert(String symbol, double minPrice, double maxPrice, double percentChange, String direction) {
        String deduplicationKey = "alert:" + symbol;
        Duration lockDuration = Duration.ofMinutes(1);
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(deduplicationKey, "locked", lockDuration);
        if (Boolean.TRUE.equals(locked)) {
            String message = String.format("🚨 <b>%s Price %s!</b>\n\n🔹 <b>Change:</b> %.2f%%\n🔹 <b>Min Price:</b> $%.2f\n🔹 <b>Max Price:</b> $%.2f\n💡 <i>Sudden price movement detected.</i>", symbol, direction, percentChange * 100, minPrice, maxPrice);
            log.info("Triggering alert for {}: {} {}", symbol, direction, percentChange);
            telegramAlertService.sendAlert(message);
        } else {
            log.debug("Alert for {} recently sent. Deduplicating.", symbol);
        }
    }
}
