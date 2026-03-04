package com.weiluo.marketalert.service;

import com.weiluo.marketalert.config.AppProperties;
import com.weiluo.marketalert.service.MarketTradeAggregator.SymbolBar;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
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
    private final MarketTradeAggregator aggregator;
    private final TelegramAlertService telegramAlertService;
    private final AppProperties properties;
    private final ReactiveStringRedisTemplate redisTemplate;

    // In-memory BarSeries for each symbol
    private final Map<String, BarSeries> seriesMap = new ConcurrentHashMap<>();

    public Ta4jAnalysisService(StockDataIngestionService ingestionService, MarketTradeAggregator aggregator,
            TelegramAlertService telegramAlertService, AppProperties properties,
            ReactiveStringRedisTemplate redisTemplate) {
        this.ingestionService = ingestionService;
        this.aggregator = aggregator;
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

        Duration barDuration = Duration.ofSeconds(properties.ta4j().barDurationSeconds());

        aggregator.aggregateToBars(ingestionService.getTradeStream(), barDuration).flatMap(this::processBar)
                .subscribe(null, error -> log.error("Error in ta4j analysis stream", error));
    }

    private Mono<Void> processBar(SymbolBar symbolBar) {
        String symbol = symbolBar.symbol();
        BarSeries series = seriesMap.computeIfAbsent(symbol, key -> {
            BaseBarSeries newSeries = new BaseBarSeries(key);
            newSeries.setMaximumBarCount(200); // Keep max 200 bars in memory to prevent leak
            return newSeries;
        });

        series.addBar(symbolBar.bar());
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
        org.ta4j.core.indicators.EMAIndicator signalEMAMACD = new org.ta4j.core.indicators.EMAIndicator(macd,
                properties.ta4j().macdSignal());

        // Bullish Rule: MACD crosses up signal AND RSI < 70 (not overbought)
        Rule bullishRule = new CrossedUpIndicatorRule(macd, signalEMAMACD).and(new UnderIndicatorRule(rsi, 70));

        // Bearish Rule: MACD crosses down signal AND RSI > 30 (not oversold)
        Rule bearishRule = new CrossedDownIndicatorRule(macd, signalEMAMACD).and(new OverIndicatorRule(rsi, 30));

        if (bullishRule.isSatisfied(index)) {
            return triggerAlert(symbol, "BULLISH 📈", closePrice.getValue(index).doubleValue(),
                    rsi.getValue(index).doubleValue());
        } else if (bearishRule.isSatisfied(index)) {
            return triggerAlert(symbol, "BEARISH 📉", closePrice.getValue(index).doubleValue(),
                    rsi.getValue(index).doubleValue());
        }

        return Mono.empty();
    }

    private Mono<Void> triggerAlert(String symbol, String signal, double currentPrice, double rsiValue) {
        String deduplicationKey = "ta4j_alert:" + symbol + ":" + signal;
        Duration lockDuration = Duration.ofMinutes(5); // Throttle identical signals

        return redisTemplate.opsForValue().setIfAbsent(deduplicationKey, "locked", lockDuration).flatMap(locked -> {
            if (Boolean.TRUE.equals(locked)) {
                String message = String.format(
                        "📊 <b>%s Technical Alert!</b>\n\n" + "🔹 <b>Signal:</b> %s\n" + "🔹 <b>Price:</b> $%.2f\n"
                                + "🔹 <b>RSI:</b> %.2f\n" + "💡 <i>MACD Crossover Detected.</i>",
                        symbol, signal, currentPrice, rsiValue);
                log.info("Triggering ta4j alert for {}: {}", symbol, signal);
                return telegramAlertService.sendAlert(message);
            } else {
                log.debug("TA alert for {} recently sent. Deduplicating.", symbol);
                return Mono.empty();
            }
        });
    }
}
