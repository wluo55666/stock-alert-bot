package com.weiluo.marketalert.service;

import com.weiluo.marketalert.config.AppProperties;
import com.weiluo.marketalert.model.SymbolBar;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;
import org.ta4j.core.Rule;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class Ta4jAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(Ta4jAnalysisService.class);

    private final StockDataIngestionService ingestionService;
    private final TelegramAlertService telegramAlertService;
    private final AppProperties properties;
    private final ReactiveStringRedisTemplate redisTemplate;

    // In-memory BarSeries for each symbol
    private final Map<String, BarSeries> seriesMap = new ConcurrentHashMap<>();

    public Ta4jAnalysisService(StockDataIngestionService ingestionService, TelegramAlertService telegramAlertService,
            AppProperties properties, ReactiveStringRedisTemplate redisTemplate) {
        this.ingestionService = ingestionService;
        this.telegramAlertService = telegramAlertService;
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void setupAnalysis() {
        if (properties.ta4j() == null) {
            log.warn("ta4j config missing. Advanced technical analysis disabled.");
            return;
        }

        ingestionService.getBarStream().flatMap(this::processBar).subscribe(null,
                error -> log.error("Error in ta4j analysis stream", error));
    }

    private Mono<Void> processBar(SymbolBar symbolBar) {
        String symbol = symbolBar.symbol();
        BarSeries series = seriesMap.computeIfAbsent(symbol, key -> {
            BaseBarSeries newSeries = new BaseBarSeriesBuilder().withName(key)
                    .withNumFactory(org.ta4j.core.num.DoubleNumFactory.getInstance()).build();
            newSeries.setMaximumBarCount(200); // Keep max 200 bars in memory to prevent leak
            return newSeries;
        });

        synchronized (series) {
            try {
                if (series.isEmpty()) {
                    series.addBar(symbolBar.bar());
                } else {
                    java.time.Instant lastEndTime = series.getLastBar().getEndTime();
                    java.time.Instant newEndTime = symbolBar.bar().getEndTime();

                    if (newEndTime.equals(lastEndTime)) {
                        series.addBar(symbolBar.bar(), true); // replace current minute's bar
                    } else if (newEndTime.isAfter(lastEndTime)) {
                        series.addBar(symbolBar.bar());
                    } else {
                        // Ignore older bars
                        return Mono.empty();
                    }
                }
            } catch (IllegalArgumentException e) {
                log.warn("Failed to add bar to series for {}: {}", symbol, e.getMessage());
                return Mono.empty();
            }
        }
        int endIndex = series.getEndIndex();

        return analyze(symbol, series, endIndex);
    }

    private Mono<Void> analyze(String symbol, BarSeries series, int index) {
        if (series.getBarCount() < properties.ta4j().macdLong() + properties.ta4j().macdSignal()) {
            // Not enough bars to calculate MACD reliably
            return Mono.empty();
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // RSI Indicator
        RSIIndicator rsi = new RSIIndicator(closePrice, properties.ta4j().rsiTimeframe());

        // MACD Indicator
        MACDIndicator macd = new MACDIndicator(closePrice, properties.ta4j().macdShort(), properties.ta4j().macdLong());
        // Corrected to use EMAIndicator directly after import
        EMAIndicator signalEMAMACD = new EMAIndicator(macd, properties.ta4j().macdSignal());

        // Bullish Rule: MACD crosses up signal AND MACD is below zero (indicates a reversal from a downtrend) AND RSI > 30 AND RSI < 70
        Rule bullishRule = new CrossedUpIndicatorRule(macd, signalEMAMACD)
                .and(new UnderIndicatorRule(macd, 0))
                .and(new UnderIndicatorRule(rsi, 70));

        // Bearish Rule: MACD crosses down signal AND MACD is above zero (indicates a reversal from an uptrend) AND RSI < 70 AND RSI > 30
        Rule bearishRule = new CrossedDownIndicatorRule(macd, signalEMAMACD)
                .and(new OverIndicatorRule(macd, 0))
                .and(new OverIndicatorRule(rsi, 30));

        if (bullishRule.isSatisfied(index)) {
            String explanation = "Reversal from a downtrend detected! The stock has been selling off but just caught a strong upward bounce on the 15-minute chart. RSI confirms it is not overbought yet.";
            return triggerAlert(symbol, "BULLISH 📈", closePrice.getValue(index).doubleValue(),
                    rsi.getValue(index).doubleValue(), explanation);
        } else if (bearishRule.isSatisfied(index)) {
            String explanation = "Rejection from an uptrend detected! The stock was rallying but just lost momentum and crossed downward on the 15-minute chart. RSI confirms it is not oversold yet.";
            return triggerAlert(symbol, "BEARISH 📉", closePrice.getValue(index).doubleValue(),
                    rsi.getValue(index).doubleValue(), explanation);
        }

        return Mono.empty();
    }

    private Mono<Void> triggerAlert(String symbol, String signal, double currentPrice, double rsiValue, String explanation) {
        String deduplicationKey = "ta4j_alert:" + symbol + ":" + signal;
        Duration lockDuration = Duration.ofMinutes(30); // Throttles identical signals for 30 minutes to reduce noise

        return redisTemplate.opsForValue().setIfAbsent(deduplicationKey, "locked", lockDuration).flatMap(locked -> {
            if (Boolean.TRUE.equals(locked)) {
                String message = String.format(
                        "📊 <b>%s Swing Trade Alert!</b>\n\n" + 
                        "🔹 <b>Direction:</b> %s\n" + 
                        "🔹 <b>Current Price:</b> $%.2f\n" + 
                        "🔹 <b>RSI Strength:</b> %.2f/100\n\n" + 
                        "💡 <b>What this means:</b> <i>%s</i>",
                        symbol, signal, currentPrice, rsiValue, explanation);
                log.info("Triggering ta4j alert for {}: {}", symbol, signal);
                return telegramAlertService.sendAlert(message);
            } else {
                log.debug("TA alert for {} recently sent. Deduplicating.", symbol);
                return Mono.empty();
            }
        });
    }
}
