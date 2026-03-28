package com.weiluo.marketalert.service;
import com.weiluo.marketalert.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import java.util.Map;

@Service
public class TelegramAlertService {
    private static final Logger log = LoggerFactory.getLogger(TelegramAlertService.class);
    private final RestClient restClient;
    private final AppProperties properties;

    public TelegramAlertService(AppProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.baseUrl("https://api.telegram.org").build();
    }

    public void sendAlert(String message) {
        if (properties.telegram() == null || properties.telegram().botToken().isBlank()) {
            log.warn("Telegram bot token not configured. Skipping alert: {}", message);
            return;
        }
        String url = "/bot" + properties.telegram().botToken() + "/sendMessage";
        Map<String, Object> body = Map.of("chat_id", properties.telegram().chatId(), "text", message, "parse_mode", "HTML");
        try {
            restClient.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
            log.info("Alert sent to Telegram successfully");
        } catch (Exception e) {
            log.error("Failed to send Telegram alert: {}", e.getMessage());
        }
    }
}
