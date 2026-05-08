package com.weiluo.marketalert.service;

import com.weiluo.marketalert.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class OffHoursAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(OffHoursAnalysisService.class);
    private static final ZoneId MARKET_TZ = ZoneId.of("America/New_York");
    private static final Duration SNAPSHOT_DEDUPE_TTL = Duration.ofDays(7);

    private final AppProperties properties;
    private final RestClient restClient;
    private final StringRedisTemplate redisTemplate;
    private final TelegramAlertService telegramAlertService;
    private final TelegramAlertFormatter telegramAlertFormatter;
    private final MarketNewsTool marketNewsTool;

    public OffHoursAnalysisService(AppProperties properties,
                                   RestClient.Builder restClientBuilder,
                                   StringRedisTemplate redisTemplate,
                                   TelegramAlertService telegramAlertService,
                                   TelegramAlertFormatter telegramAlertFormatter,
                                   MarketNewsTool marketNewsTool) {
        this.properties = properties;
        this.restClient = restClientBuilder.baseUrl("https://query1.finance.yahoo.com/v8/finance/chart")
                .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build();
        this.redisTemplate = redisTemplate;
        this.telegramAlertService = telegramAlertService;
        this.telegramAlertFormatter = telegramAlertFormatter;
        this.marketNewsTool = marketNewsTool;
    }

    @Scheduled(fixedRate = 60000)
    public void analyzeOffHours() {
        if (properties.offHours() == null || !properties.offHours().enabled()) return;
        if (!isOffHoursNow()) return;

        List<String> symbols = properties.symbols().stream().filter(sym -> !sym.startsWith("TEST_")).toList();
        for (String symbol : symbols) {
            try {
                OffHoursSnapshot snapshot = fetchOffHoursSnapshot(symbol);
                if (snapshot == null) {
                    log.info("Off-hours decision symbol={} session=UNKNOWN action=NO_TRIGGER newsRequested=false newsResult=not_requested newsDisplayed=false reason=no_snapshot", symbol);
                    continue;
                }
                if (Math.abs(snapshot.movePercent()) < properties.offHours().watchThresholdPercent()) {
                    log.info("Off-hours decision symbol={} session={} action=NO_TRIGGER previousClose={} latestPrice={} observedAt={} move={} threshold={} newsRequested=false newsResult=not_requested newsDisplayed=false reason=below_threshold",
                            symbol,
                            snapshot.sessionLabel(),
                            snapshot.previousClose(),
                            snapshot.latestPrice(),
                            snapshot.observedAt(),
                            snapshot.movePercent(),
                            properties.offHours().watchThresholdPercent());
                    continue;
                }
                triggerOffHoursAlert(symbol, snapshot);
                Thread.sleep(300);
            } catch (Exception e) {
                log.warn("Off-hours analysis failed for {}: {}", symbol, e.getMessage());
            }
        }
    }

    private boolean isOffHoursNow() {
        ZonedDateTime now = ZonedDateTime.now(MARKET_TZ);
        DayOfWeek day = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) return true;
        int hhmm = now.getHour() * 100 + now.getMinute();
        return hhmm < 930 || hhmm >= 1600;
    }

    private OffHoursSnapshot fetchOffHoursSnapshot(String symbol) {
        YahooChartResponse response = restClient.get().uri("/{symbol}?interval=1m&range=1d&includePrePost=true", symbol).retrieve().body(YahooChartResponse.class);
        if (response == null || response.chart() == null || response.chart().result() == null || response.chart().result().isEmpty()) return null;

        Result result = response.chart().result().get(0);
        if (result.timestamp() == null || result.timestamp().isEmpty()) return null;
        Quote quote = result.indicators().quote().get(0);

        int latestIdx = findLatestOffHoursIndex(result.timestamp(), quote);
        if (latestIdx < 0) return null;

        int previousCloseIdx = findLastRegularSessionIndex(result.timestamp(), quote);
        if (previousCloseIdx < 0) return null;

        double latestPrice = quote.close().get(latestIdx);
        double previousClose = quote.close().get(previousCloseIdx);
        if (previousClose <= 0) return null;

        double movePercent = (latestPrice - previousClose) / previousClose;
        String sessionLabel = classifySession(result.timestamp().get(latestIdx));
        return new OffHoursSnapshot(previousClose, latestPrice, movePercent, sessionLabel, Instant.ofEpochSecond(result.timestamp().get(latestIdx)));
    }

    private int findLatestOffHoursIndex(List<Long> timestamps, Quote quote) {
        for (int i = timestamps.size() - 1; i >= 0; i--) {
            Double close = quote.close().get(i);
            if (close == null) continue;
            if (isOffHoursTimestamp(timestamps.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private int findLastRegularSessionIndex(List<Long> timestamps, Quote quote) {
        for (int i = timestamps.size() - 1; i >= 0; i--) {
            Double close = quote.close().get(i);
            if (close == null) continue;
            if (isRegularSessionTimestamp(timestamps.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private boolean isRegularSessionTimestamp(long epochSecond) {
        ZonedDateTime ts = Instant.ofEpochSecond(epochSecond).atZone(MARKET_TZ);
        int hhmm = ts.getHour() * 100 + ts.getMinute();
        return hhmm >= 930 && hhmm < 1600 && ts.getDayOfWeek() != DayOfWeek.SATURDAY && ts.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    private boolean isOffHoursTimestamp(long epochSecond) {
        ZonedDateTime ts = Instant.ofEpochSecond(epochSecond).atZone(MARKET_TZ);
        int hhmm = ts.getHour() * 100 + ts.getMinute();
        return (hhmm < 930 || hhmm >= 1600) || ts.getDayOfWeek() == DayOfWeek.SATURDAY || ts.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    private String classifySession(long epochSecond) {
        ZonedDateTime ts = Instant.ofEpochSecond(epochSecond).atZone(MARKET_TZ);
        int hhmm = ts.getHour() * 100 + ts.getMinute();
        if (hhmm < 930) return "Premarket";
        if (hhmm >= 1600) return "After-hours";
        return "Regular";
    }

    private void triggerOffHoursAlert(String symbol, OffHoursSnapshot snapshot) {
        String snapshotKey = snapshotDedupeKey(symbol, snapshot);
        Boolean snapshotSeen = redisTemplate.opsForValue().setIfAbsent(snapshotKey, "sent", SNAPSHOT_DEDUPE_TTL);
        if (!Boolean.TRUE.equals(snapshotSeen)) {
            log.info("Off-hours decision symbol={} session={} action=SUPPRESS previousClose={} latestPrice={} observedAt={} move={} newsRequested=false newsResult=not_requested newsDisplayed=false reason=duplicate_snapshot",
                    symbol,
                    snapshot.sessionLabel(),
                    snapshot.previousClose(),
                    snapshot.latestPrice(),
                    snapshot.observedAt(),
                    snapshot.movePercent());
            return;
        }

        String cooldownKey = "offhours_alert:" + symbol + ":" + snapshot.sessionLabel().toLowerCase(Locale.ROOT);
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(cooldownKey, "locked", Duration.ofMinutes(properties.offHours().cooldownMinutes()));
        if (!Boolean.TRUE.equals(locked)) {
            log.info("Off-hours decision symbol={} session={} action=SUPPRESS previousClose={} latestPrice={} observedAt={} move={} newsRequested=false newsResult=not_requested newsDisplayed=false reason=cooldown",
                    symbol,
                    snapshot.sessionLabel(),
                    snapshot.previousClose(),
                    snapshot.latestPrice(),
                    snapshot.observedAt(),
                    snapshot.movePercent());
            return;
        }

        boolean useNews = Math.abs(snapshot.movePercent()) >= properties.offHours().alertThresholdPercent();
        String newsContext = useNews ? marketNewsTool.getLatestNews(symbol) : "";
        String newsCatalyst = (newsContext == null || newsContext.isBlank() || newsContext.startsWith("No relevant")) ? "" : newsContext;

        StructuredTradingAlert alert = new StructuredTradingAlert(
                String.format("%s is %s %.2f%% versus the last regular-session close in %s.", symbol, snapshot.movePercent() >= 0 ? "up" : "down", Math.abs(snapshot.movePercent() * 100), snapshot.sessionLabel().toLowerCase(Locale.ROOT)),
                String.format("The stock moved from %.2f to %.2f outside normal market hours.", snapshot.previousClose(), snapshot.latestPrice()),
                String.format("watch whether %s holds this move into the next regular session.", symbol),
                String.format("a fast reversal back toward %.2f would weaken the setup.", snapshot.previousClose()),
                newsCatalyst,
                useNews,
                useNews && !newsCatalyst.isBlank()
        );

        String signal = snapshot.movePercent() >= 0 ? snapshot.sessionLabel().toUpperCase(Locale.ROOT) + " BREAKOUT" : snapshot.sessionLabel().toUpperCase(Locale.ROOT) + " DROP";
        String message = telegramAlertFormatter.formatSlidingWindowAlert(symbol, signal, Math.abs(snapshot.movePercent()) >= properties.offHours().alertThresholdPercent() ? 4 : 3, alert);
        log.info("Off-hours decision symbol={} session={} action=ALERT previousClose={} latestPrice={} observedAt={} move={} newsRequested={} newsResult={} newsDisplayed={}",
                symbol,
                snapshot.sessionLabel(),
                snapshot.previousClose(),
                snapshot.latestPrice(),
                snapshot.observedAt(),
                snapshot.movePercent(),
                useNews,
                useNews ? (alert.newsFound() ? "found" : "empty") : "not_requested",
                alert.newsFound());
        telegramAlertService.sendAlert(message);
    }

    private String snapshotDedupeKey(String symbol, OffHoursSnapshot snapshot) {
        long priceBasisPoints = Math.round(snapshot.latestPrice() * 10_000);
        return "offhours_alert_snapshot:"
                + symbol + ':'
                + snapshot.sessionLabel().toLowerCase(Locale.ROOT) + ':'
                + snapshot.observedAt().getEpochSecond() + ':'
                + priceBasisPoints;
    }

    private record OffHoursSnapshot(double previousClose, double latestPrice, double movePercent, String sessionLabel, Instant observedAt) {
    }

    public record YahooChartResponse(Chart chart) {}
    public record Chart(List<Result> result, Object error) {}
    public record Result(List<Long> timestamp, Indicators indicators) {}
    public record Indicators(List<Quote> quote) {}
    public record Quote(List<Double> open, List<Double> high, List<Double> low, List<Double> close, List<Long> volume) {}
}
