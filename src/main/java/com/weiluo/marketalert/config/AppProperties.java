package com.weiluo.marketalert.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
                List<String> symbols,
                SlidingWindow slidingWindow,
                Telegram telegram,
                Finnhub finnhub) {
        public record SlidingWindow(
                        int durationSeconds,
                        double thresholdPercent) {
        }

        public record Telegram(
                        String botToken,
                        String chatId) {
        }

        public record Finnhub(
                        String apiKey) {
        }
}
