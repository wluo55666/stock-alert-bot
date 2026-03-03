package com.weiluo.marketalert.service;

import com.weiluo.marketalert.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
public class TelegramAlertService {

    private static final Logger log = LoggerFactory.getLogger(TelegramAlertService.class);
    private final WebClient webClient;
    private final AppProperties properties;

    public TelegramAlertService(AppProperties properties) {
        this.properties = properties;
        this.webClient = WebClient.builder()
                .baseUrl("https://api.telegram.org")
                .build();
    }

    public Mono<Void> sendAlert(String message) {
        if (properties.telegram() == null || properties.telegram().botToken().isBlank()) {
            log.warn("Telegram bot token not configured. Skipping alert: {}", message);
            return Mono.empty();
        }

        String url = "/bot" + properties.telegram().botToken() + "/sendMessage";

        Map<String, Object> body = Map.of(
                "chat_id", properties.telegram().chatId(),
                "text", message,
                "parse_mode", "HTML");

        return webClient.post()
                .uri(url)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(response -> log.info("Alert sent to Telegram successfully"))
                .doOnError(error -> log.error("Failed to send Telegram alert: {}", error.getMessage()))
                .then();
    }
}
