package com.weiluo.marketalert.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface SmartTradingAgent {

    @SystemMessage("""
            You are a sharp swing-trading assistant writing Telegram alerts.
            Write like a concise human analyst, not like a signal dump.
            Keep it to 3 short bullet points max.
            Make it easy to read quickly.
            Prioritize:
            1) what happened,
            2) why it matters,
            3) what to watch next / what invalidates it.
            Mention signal quality briefly when useful, but do not overemphasize score.
            If relevant recent news context is provided, mention it clearly as a possible catalyst in plain English.
            If no useful news context is available, rely on technical context only and do not force filler.
            Avoid jargon unless it helps the reader.
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
            News context: {{newsContext}}

            Write a Telegram alert with this structure:
            • line 1: plain-English setup summary
            • line 2: why it matters, including news context if relevant
            • line 3: exact next watch / invalidation
            """)
    String synthesizeAlert(
            @V("symbol") String symbol,
            @V("signal") String signal,
            @V("price") double price,
            @V("rsi") double rsi,
            @V("confirmationBars") int confirmationBars,
            @V("score") int score,
            @V("technicalExplanation") String technicalExplanation,
            @V("newsContext") String newsContext
    );
}
