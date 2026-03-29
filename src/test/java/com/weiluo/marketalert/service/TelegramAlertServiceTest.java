package com.weiluo.marketalert.service;
import com.weiluo.marketalert.config.AppProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelegramAlertServiceTest {
    private MockWebServer mockWebServer;
    private TelegramAlertService telegramAlertService;
    private AppProperties properties;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        String baseUrl = mockWebServer.url("/").toString();
        properties = new AppProperties(null, null, null, new AppProperties.Telegram("test_bot_token", "test_chat_id", baseUrl), null);
        RestClient.Builder builder = RestClient.builder();
        telegramAlertService = new TelegramAlertService(properties, builder);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void testSendAlertSuccess() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody("{\"ok\":true}"));
        telegramAlertService.sendAlert("Test Alert Message");
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("POST", recordedRequest.getMethod());
        assertTrue(recordedRequest.getBody().readUtf8().contains("\"text\":\"Test Alert Message\""));
    }
}
