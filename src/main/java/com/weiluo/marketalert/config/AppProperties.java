package com.weiluo.marketalert.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(List<String> symbols, SlidingWindow slidingWindow, Ta4j ta4j, Telegram telegram,
                Finnhub finnhub) {
        public record SlidingWindow(int durationSeconds, double thresholdPercent) {
        }

        public record Ta4j(int barDurationSeconds, int rsiTimeframe, int macdShort, int macdLong, int macdSignal) {
        }

        public record Telegram(String botToken, String chatId) {
        }

        public record Finnhub(String apiKey) {
        }
}
