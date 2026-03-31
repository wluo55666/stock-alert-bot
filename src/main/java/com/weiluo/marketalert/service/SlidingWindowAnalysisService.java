package com.weiluo.marketalert.service;

import com.weiluo.marketalert.config.AppProperties;
import com.weiluo.marketalert.model.SymbolBar;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
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
        if (properties.slidingWindow() == null) {
            log.warn("Sliding window config missing. Analysis disabled.");
        }
    }

    @Async
    @EventListener
    public void processBar(SymbolBar symbolBar) {
        if (properties.slidingWindow() == null) return;

        String key = "window:" + symbolBar.symbol();
        long now = symbolBar.bar().getEndTime().toEpochMilli();
        double closePrice = symbolBar.bar().getClosePrice().doubleValue();
        long windowStart = now - (properties.slidingWindow().durationSeconds() * 1000L);

        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
        zSetOps.add(key, closePrice + ":" + now, now);
        zSetOps.removeRangeByScore(key, 0.0, windowStart - 1);

        Set<String> items = zSetOps.range(key, 0, -1);
        if (items != null) {
            analyzeWindow(symbolBar.symbol(), new ArrayList<>(items), closePrice);
        }
    }

    private void analyzeWindow(String symbol, List<String> window, double currentPrice) {
        WindowAnalysis analysis = buildAnalysis(window, currentPrice);
        if (analysis == null) return;

        double threshold = properties.slidingWindow().thresholdPercent();
        if (Math.abs(analysis.netPercentChange()) < threshold) return;
        if (analysis.confirmationCount() < properties.slidingWindow().confirmationBars()) return;
        if (analysis.score() < properties.slidingWindow().minimumScore()) {
            log.debug("Suppressing sliding-window alert for {} because score {} is below minimum {}", symbol, analysis.score(), properties.slidingWindow().minimumScore());
            return;
        }

        String direction = analysis.netPercentChange() > 0 ? "BREAKOUT" : "BREAKDOWN";
        triggerAlert(symbol, analysis.startPrice(), currentPrice, analysis.netPercentChange(), direction, analysis.confirmationCount(), analysis.score());
    }

    private WindowAnalysis buildAnalysis(List<String> window, double currentPrice) {
        if (window.size() < 2) return null;

        List<WindowPoint> points = window.stream()
                .map(this::parsePoint)
                .filter(point -> point != null)
                .sorted(Comparator.comparingLong(WindowPoint::timestamp))
                .toList();

        if (points.size() < 2) return null;

        double startPrice = points.getFirst().price();
        if (startPrice <= 0) return null;

        double netPercentChange = (currentPrice - startPrice) / startPrice;
        int confirmationCount = countDirectionalConfirmation(points);
        int score = scoreSignal(netPercentChange, confirmationCount);
        return new WindowAnalysis(startPrice, netPercentChange, confirmationCount, score);
    }

    private int scoreSignal(double netPercentChange, int confirmationCount) {
        int score = 0;
        double absoluteChange = Math.abs(netPercentChange);
        if (absoluteChange >= properties.slidingWindow().thresholdPercent()) score++;
        if (absoluteChange >= properties.slidingWindow().strongMovePercent()) score++;
        if (confirmationCount >= properties.slidingWindow().confirmationBars()) score++;
        if (confirmationCount >= properties.slidingWindow().confirmationBars() + 1) score++;
        return score;
    }

    private WindowPoint parsePoint(String item) {
        try {
            String[] parts = item.split(":");
            return new WindowPoint(Double.parseDouble(parts[0]), Long.parseLong(parts[1]));
        } catch (Exception e) {
            log.debug("Skipping malformed window point {}", item);
            return null;
        }
    }

    private int countDirectionalConfirmation(List<WindowPoint> points) {
        if (points.size() < 2) return 0;

        int confirmation = 1;
        double lastDiff = points.get(points.size() - 1).price() - points.get(points.size() - 2).price();
        if (lastDiff == 0) return 0;
        boolean upward = lastDiff > 0;

        for (int i = points.size() - 1; i > 0; i--) {
            double diff = points.get(i).price() - points.get(i - 1).price();
            if (diff == 0) break;
            if ((diff > 0) == upward) {
                confirmation++;
            } else {
                break;
            }
        }
        return confirmation;
    }

    private void triggerAlert(String symbol, double startPrice, double currentPrice, double percentChange, String direction, int confirmationCount, int score) {
        String deduplicationKey = "alert:" + symbol;
        Duration lockDuration = Duration.ofMinutes(properties.slidingWindow().cooldownMinutes());
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(deduplicationKey, "locked", lockDuration);
        if (Boolean.TRUE.equals(locked)) {
            String message = String.format(
                    "🚨 <b>%s %s</b>\n\n🔹 <b>Move:</b> %.2f%% in last %d min\n🔹 <b>From:</b> $%.2f → <b>Now:</b> $%.2f\n🔹 <b>Confirmation:</b> %d bar(s) same direction\n🔹 <b>Score:</b> %d/4\n💡 <i>Action:</i> Watch for follow-through before chasing.",
                    symbol,
                    direction,
                    percentChange * 100,
                    properties.slidingWindow().durationSeconds() / 60,
                    startPrice,
                    currentPrice,
                    confirmationCount,
                    score
            );
            log.info("Triggering sliding-window alert for {}: {} {} (score={})", symbol, direction, percentChange, score);
            telegramAlertService.sendAlert(message);
        } else {
            log.debug("Alert for {} recently sent. Deduplicating.", symbol);
        }
    }

    private record WindowPoint(double price, long timestamp) {
    }

    private record WindowAnalysis(double startPrice, double netPercentChange, int confirmationCount, int score) {
    }
}
