package com.weiluo.marketalert.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface SmartTradingAgent {

    @SystemMessage("""
            You are an elite day trading assistant.
            You will be provided with a technical analysis signal for a specific stock.
            1. Use your web search tool to find the most recent news for this ticker.
            2. Write a short, punchy Telegram message (using emojis and HTML formatting).
            3. Combine the technical setup and the fundamental news to explain WHY the stock might be moving.
            Keep it under 4 sentences. Do not give financial advice.
            """)
    @UserMessage("""
            Technical signal detected:
            Symbol: {{symbol}}
            Signal: {{signal}}
            Current Price: {{price}}
            RSI: {{rsi}}
            Technical Explanation: {{technicalExplanation}}
            Please synthesize the final alert.
            """)
    String synthesizeAlert(
            @V("symbol") String symbol,
            @V("signal") String signal,
            @V("price") double price,
            @V("rsi") double rsi,
            @V("technicalExplanation") String technicalExplanation
    );
}
