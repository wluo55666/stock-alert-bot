package com.weiluo.marketalert.service;

import com.weiluo.marketalert.config.AppProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

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

        properties = new AppProperties(
                null,
                null,
                new AppProperties.Telegram("test_bot_token", "test_chat_id"),
                null
        );

        // Injecting the local mock web server via reflection or rebuilding
        // We can create a test-specific instance or use ReflectionTestUtils 
        // to swap the WebClient
        telegramAlertService = new TelegramAlertService(properties);
        
        // We need to swap the WebClient base URL to the mock server
        // Using Reflection is a simple way for the test
        org.springframework.test.util.ReflectionTestUtils.setField(
                telegramAlertService, 
                "webClient", 
                WebClient.builder().baseUrl(mockWebServer.url("/").toString()).build()
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void testSendAlertSuccess() throws InterruptedException {
        // Enqueue a successful response from Telegram API
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"ok\":true}"));

        String message = "Test Alert Message";

        StepVerifier.create(telegramAlertService.sendAlert(message))
                .verifyComplete();

        // Verify the HTTP Request
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        
        assertEquals("POST", recordedRequest.getMethod());
        assertEquals("/bottest_bot_token/sendMessage", recordedRequest.getPath());
        
        String requestBody = recordedRequest.getBody().readUtf8();
        assertTrue(requestBody.contains("\"chat_id\":\"test_chat_id\""));
        assertTrue(requestBody.contains("\"text\":\"Test Alert Message\""));
    }

    @Test
    void testMissingTokenSkipsAlert() throws InterruptedException {
        // Change properties to simulate missing token
        properties = new AppProperties(null, null, new AppProperties.Telegram("", "test_chat_id"), null);
        telegramAlertService = new TelegramAlertService(properties);

        StepVerifier.create(telegramAlertService.sendAlert("Test Message"))
                .verifyComplete();

        // Mock web server should NOT receive any request
        assertEquals(0, mockWebServer.getRequestCount());
    }
}
