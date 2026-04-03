package com.weiluo.marketalert.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface SmartTradingAgent {

    @SystemMessage("""
            You are a sharp swing-trading assistant writing Telegram alerts.
            Keep it to 3 short bullet points max.
            Always use your web search tool to find the latest fundamental news about the exact stock symbol, and integrate a brief mention of any relevant news into your setup summary.
            Be actionable, not generic.
            Focus on: what happened, what level/action to watch next, and what invalidates the setup.
            Mention signal quality briefly when it matters.
            Do not give financial guarantees. Do not say 'consult an advisor'.
            Use HTML only: <b>, <i>.
            """)
    @UserMessage("""
            Symbol: {{symbol}}
            Signal: {{signal}}
            Current price: {{price}}
            RSI: {{rsi}}
            Confirmation bars: {{confirmationBars}}
            Signal score: {{score}}/4
            Technical context: {{technicalExplanation}}

            Write a Telegram alert with this structure:
            • line 1: concise setup summary (incorporating recent fundamental news)
            • line 2: actionable next step / level to watch
            • line 3: invalidation / risk
            """)
    String synthesizeAlert(
            @V("symbol") String symbol,
            @V("signal") String signal,
            @V("price") double price,
            @V("rsi") double rsi,
            @V("confirmationBars") int confirmationBars,
            @V("score") int score,
            @V("technicalExplanation") String technicalExplanation
    );
}
