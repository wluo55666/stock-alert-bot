package com.weiluo.marketalert.service;

import com.weiluo.marketalert.config.AppProperties;
import com.weiluo.marketalert.model.SymbolBar;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Rule;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;

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
    private final MarketNewsTool marketNewsTool;
    private final Map<String, BarSeries> seriesMap = new ConcurrentHashMap<>();

    public Ta4jAnalysisService(TelegramAlertService telegramAlertService, AppProperties properties, StringRedisTemplate redisTemplate, SmartTradingAgent smartTradingAgent, MarketNewsTool marketNewsTool) {
        this.telegramAlertService = telegramAlertService;
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.smartTradingAgent = smartTradingAgent;
        this.marketNewsTool = marketNewsTool;
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

        Rule bullishRule = new CrossedUpIndicatorRule(macd, signalEMAMACD)
                .and(new UnderIndicatorRule(macd, 0))
                .and(new UnderIndicatorRule(rsi, 70));
        Rule bearishRule = new CrossedDownIndicatorRule(macd, signalEMAMACD)
                .and(new OverIndicatorRule(macd, 0))
                .and(new OverIndicatorRule(rsi, 30));

        if (bullishRule.isSatisfied(index)) {
            int score = scoreSignal(macd.getValue(index).doubleValue(), rsi.getValue(index).doubleValue(), closePrice, index, true, properties.ta4j().confirmationBars());
            boolean confirmed = hasDirectionalConfirmation(closePrice, index, true, properties.ta4j().confirmationBars());
            if (score >= properties.ta4j().minimumScore() && confirmed) {
                triggerAlert(symbol, "BULLISH REVERSAL 📈", closePrice.getValue(index).doubleValue(), rsi.getValue(index).doubleValue(), properties.ta4j().confirmationBars(), score, "MACD crossed above signal below zero, with RSI still below overbought. This suggests a potential reversal with early momentum confirmation.");
            } else {
                log.info("TA decision symbol={} signal=BULLISH_REVERSAL action=SUPPRESS score={} minimumScore={} confirmation={} newsUsed=false reason=score_or_confirmation", symbol, score, properties.ta4j().minimumScore(), confirmed);
            }
        } else if (bearishRule.isSatisfied(index)) {
            int score = scoreSignal(macd.getValue(index).doubleValue(), rsi.getValue(index).doubleValue(), closePrice, index, false, properties.ta4j().confirmationBars());
            boolean confirmed = hasDirectionalConfirmation(closePrice, index, false, properties.ta4j().confirmationBars());
            if (score >= properties.ta4j().minimumScore() && confirmed) {
                triggerAlert(symbol, "BEARISH REVERSAL 📉", closePrice.getValue(index).doubleValue(), rsi.getValue(index).doubleValue(), properties.ta4j().confirmationBars(), score, "MACD crossed below signal above zero, with RSI still above oversold. This suggests fading upside momentum and a potential downside reversal.");
            } else {
                log.info("TA decision symbol={} signal=BEARISH_REVERSAL action=SUPPRESS score={} minimumScore={} confirmation={} newsUsed=false reason=score_or_confirmation", symbol, score, properties.ta4j().minimumScore(), confirmed);
            }
        } else {
            log.info("TA decision symbol={} signal=NONE action=NO_TRIGGER newsUsed=false reason=no_crossover", symbol);
        }
    }

    private int scoreSignal(double macdValue, double rsiValue, ClosePriceIndicator closePrice, int index, boolean bullish, int confirmationBars) {
        int score = 1;
        if (hasDirectionalConfirmation(closePrice, index, bullish, confirmationBars)) score++;
        if (bullish && rsiValue <= 45) score++;
        if (!bullish && rsiValue >= 55) score++;
        if (bullish && macdValue < -0.1) score++;
        if (!bullish && macdValue > 0.1) score++;
        return score;
    }

    private boolean hasDirectionalConfirmation(ClosePriceIndicator closePrice, int index, boolean bullish, int confirmationBars) {
        if (confirmationBars <= 1) return true;
        if (index - confirmationBars + 1 < 1) return false;

        for (int i = index; i > index - confirmationBars + 1; i--) {
            double current = closePrice.getValue(i).doubleValue();
            double previous = closePrice.getValue(i - 1).doubleValue();
            if (bullish && current <= previous) return false;
            if (!bullish && current >= previous) return false;
        }
        return true;
    }

    private void triggerAlert(String symbol, String signal, double currentPrice, double rsiValue, int confirmationBars, int score, String explanation) {
        String deduplicationKey = "ta4j_alert:" + symbol;
        Duration lockDuration = Duration.ofMinutes(properties.ta4j().cooldownMinutes());
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(deduplicationKey, "locked", lockDuration);
        if (Boolean.TRUE.equals(locked)) {
            boolean useNews = score >= 4;
            String newsContext = useNews ? marketNewsTool.getLatestNews(symbol) : "No relevant news lookup performed for this alert.";
            String aiMessage = smartTradingAgent.synthesizeAlert(symbol, signal, currentPrice, rsiValue, confirmationBars, score, explanation, newsContext);
            log.info("TA decision symbol={} signal={} action=ALERT score={} minimumScore={} confirmation=true newsUsed={}", symbol, signal, score, properties.ta4j().minimumScore(), useNews);
            telegramAlertService.sendAlert(aiMessage);
        } else {
            log.info("TA decision symbol={} signal={} action=SUPPRESS score={} minimumScore={} confirmation=true newsUsed=false reason=cooldown", symbol, signal, score, properties.ta4j().minimumScore());
        }
    }
}
