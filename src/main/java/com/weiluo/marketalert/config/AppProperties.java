package com.weiluo.marketalert.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(List<String> symbols, SlidingWindow slidingWindow, Ta4j ta4j, Telegram telegram) {
        public record SlidingWindow(int durationSeconds, double thresholdPercent) {
        }

        public record Ta4j(int barDurationSeconds, int rsiTimeframe, int macdShort, int macdLong, int macdSignal) {
        }

        public record Telegram(String botToken, String chatId, String apiBaseUrl) {
                public String resolvedBaseUrl() {
                        return (apiBaseUrl == null || apiBaseUrl.isBlank()) ? "https://api.telegram.org" : apiBaseUrl;
                }
        }
}
