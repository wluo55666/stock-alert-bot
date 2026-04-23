package com.weiluo.marketalert.controller;

import com.weiluo.marketalert.model.SymbolBar;
import com.weiluo.marketalert.service.SmartTradingAgent;
import com.weiluo.marketalert.service.StockDataIngestionService;
import com.weiluo.marketalert.service.StructuredTradingAlert;
import com.weiluo.marketalert.service.TelegramAlertFormatter;
import com.weiluo.marketalert.service.TelegramAlertService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TestE2EController.class)
@TestPropertySource(properties = "app.test-endpoints-enabled=true")
@Import(TestE2EController.class)
class TestE2EControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StockDataIngestionService ingestionService;
    @MockBean
    private SmartTradingAgent smartTradingAgent;
    @MockBean
    private TelegramAlertService telegramAlertService;
    @MockBean
    private TelegramAlertFormatter telegramAlertFormatter;

    @Test
    void testInjectBarEndpoint() throws Exception {
        String jsonPayload = "{\"symbol\":\"NVDA\",\"open\":100.0,\"high\":105.0,\"low\":95.0,\"close\":102.0,\"volume\":1000.0,\"timestamp\":1700000000000}";
        mockMvc.perform(post("/api/test/inject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk());
        verify(ingestionService).injectBar(any(SymbolBar.class));
    }

    @Test
    void testTriggerAlertEndpoint() throws Exception {
        when(smartTradingAgent.synthesizeAlert(anyString(), anyString(), anyDouble(), anyDouble(), anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(new StructuredTradingAlert("summary", "why", "watch", "invalid", "news", false, false));
        when(telegramAlertFormatter.formatTaAlert(anyString(), anyString(), anyInt(), any(StructuredTradingAlert.class)))
                .thenReturn("formatted alert");

        String jsonPayload = """
                {
                  "symbol": "FAKE_E2E",
                  "signal": "BULLISH REVERSAL 📈",
                  "price": 185.0,
                  "rsi": 35.0,
                  "confirmationBars": 2,
                  "score": 4,
                  "technicalExplanation": "Artificial E2E Injection"
                }
                """;

        mockMvc.perform(post("/api/test/alert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk());

        verify(telegramAlertService).sendAlert("formatted alert");
    }
}
