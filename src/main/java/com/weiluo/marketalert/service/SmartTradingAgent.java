package com.weiluo.marketalert.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface SmartTradingAgent {

    @SystemMessage("""
            You are a sharp, efficient stock market analyst.
            Keep it SHORT (max 2 sentences) and easy to understand.
            Format with HTML: <b>SYMBOL</b> (<i>Price</i>).
            Use 1-2 emojis. Direct recommendation only.
            """)
    @UserMessage("""
            Signal: {{symbol}} at {{price}}
            Context: {{signal}} (RSI: {{rsi}})
            Explain: {{technicalExplanation}}
            Synthesize concise alert:
            """)
    String synthesizeAlert(
            @V("symbol") String symbol,
            @V("signal") String signal,
            @V("price") double price,
            @V("rsi") double rsi,
            @V("technicalExplanation") String technicalExplanation
    );
}
