package com.weiluo.marketalert.service;
import com.weiluo.marketalert.config.AppProperties;
import com.weiluo.marketalert.model.SymbolBar;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
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
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class Ta4jAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(Ta4jAnalysisService.class);
    private final TelegramAlertService telegramAlertService;
    private final AppProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final SmartTradingAgent smartTradingAgent;
    private final Map<String, BarSeries> seriesMap = new ConcurrentHashMap<>();

    public Ta4jAnalysisService(TelegramAlertService telegramAlertService, AppProperties properties, StringRedisTemplate redisTemplate, SmartTradingAgent smartTradingAgent) {
        this.telegramAlertService = telegramAlertService;
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.smartTradingAgent = smartTradingAgent;
    }

    @PostConstruct
    public void init() {
        if (properties.ta4j() == null) log.warn("ta4j config missing. Advanced technical analysis disabled.");
    }

    @Async
    @EventListener
    public void processBar(SymbolBar symbolBar) {
        if (properties.ta4j() == null) return;
        String symbol = symbolBar.symbol();
        BarSeries series = seriesMap.computeIfAbsent(symbol, key -> {
            BaseBarSeries newSeries = new BaseBarSeriesBuilder().withName(key).withNumFactory(org.ta4j.core.num.DoubleNumFactory.getInstance()).build();
            newSeries.setMaximumBarCount(200);
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
                        series.addBar(symbolBar.bar(), true);
                    } else if (newEndTime.isAfter(lastEndTime)) {
                        series.addBar(symbolBar.bar());
                    } else {
                        return;
                    }
                }
            } catch (IllegalArgumentException e) {
                log.warn("Failed to add bar to series for {}: {}", symbol, e.getMessage());
                return;
            }
        }
        analyze(symbol, series, series.getEndIndex());
    }

    private void analyze(String symbol, BarSeries series, int index) {
        if (series.getBarCount() < properties.ta4j().macdLong() + properties.ta4j().macdSignal()) return;
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        RSIIndicator rsi = new RSIIndicator(closePrice, properties.ta4j().rsiTimeframe());
        MACDIndicator macd = new MACDIndicator(closePrice, properties.ta4j().macdShort(), properties.ta4j().macdLong());
        EMAIndicator signalEMAMACD = new EMAIndicator(macd, properties.ta4j().macdSignal());
        Rule bullishRule = new CrossedUpIndicatorRule(macd, signalEMAMACD).and(new UnderIndicatorRule(macd, 0)).and(new UnderIndicatorRule(rsi, 70));
        Rule bearishRule = new CrossedDownIndicatorRule(macd, signalEMAMACD).and(new OverIndicatorRule(macd, 0)).and(new OverIndicatorRule(rsi, 30));

        if (bullishRule.isSatisfied(index)) {
            triggerAlert(symbol, "BULLISH 📈", closePrice.getValue(index).doubleValue(), rsi.getValue(index).doubleValue(), "Reversal from a downtrend detected! The stock has been selling off but just caught a strong upward bounce on the 15-minute chart. RSI confirms it is not overbought yet.");
        } else if (bearishRule.isSatisfied(index)) {
            triggerAlert(symbol, "BEARISH 📉", closePrice.getValue(index).doubleValue(), rsi.getValue(index).doubleValue(), "Rejection from an uptrend detected! The stock was rallying but just lost momentum and crossed downward on the 15-minute chart. RSI confirms it is not oversold yet.");
        }
    }

    private void triggerAlert(String symbol, String signal, double currentPrice, double rsiValue, String explanation) {
        String deduplicationKey = "ta4j_alert:" + symbol + ":" + signal;
        Duration lockDuration = Duration.ofMinutes(30);
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(deduplicationKey, "locked", lockDuration);
        if (Boolean.TRUE.equals(locked)) {
            String aiMessage = smartTradingAgent.synthesizeAlert(symbol, signal, currentPrice, rsiValue, explanation);
            log.info("Triggering ta4j alert for {}: {}", symbol, signal);
            telegramAlertService.sendAlert(aiMessage);
        } else {
            log.debug("TA alert for {} recently sent. Deduplicating.", symbol);
        }
    }
}
