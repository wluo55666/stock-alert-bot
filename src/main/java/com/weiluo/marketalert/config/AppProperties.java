package com.weiluo.marketalert.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(List<String> symbols, SlidingWindow slidingWindow, Ta4j ta4j, Telegram telegram, Ai ai) {
    public record SlidingWindow(int durationSeconds, double thresholdPercent, int confirmationBars, int cooldownMinutes) {
    }

    public record Ta4j(int barDurationSeconds, int rsiTimeframe, int macdShort, int macdLong, int macdSignal, int confirmationBars, int cooldownMinutes) {
    }

    public record Ai(String tavilyApiKey) {
    }

    public record Telegram(String botToken, String chatId, String apiBaseUrl) {
        public String resolvedBaseUrl() {
            return (apiBaseUrl == null || apiBaseUrl.isBlank()) ? "https://api.telegram.org" : apiBaseUrl;
        }
    }
}
