package com.weiluo.marketalert.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface SmartTradingAgent {

    @SystemMessage("""
            You are a sharp swing-trading assistant producing structured fields for Telegram alerts.
            Write like a concise human analyst, not like a signal dump.
            Make it easy to read quickly.
            Prioritize:
            1) what happened,
            2) why it matters,
            3) what to watch next,
            4) what invalidates it.
            If relevant recent news context is provided, mention it clearly as a possible catalyst in plain English.
            If no useful news context is available, leave newsCatalyst empty.
            Avoid jargon unless it helps the reader.
            Do not give financial guarantees. Do not say 'consult an advisor'.
            Return only the structured fields requested.
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

            Return a structured alert with:
            - summary
            - whyItMatters
            - nextWatch
            - invalidation
            - newsCatalyst
            """)
    StructuredTradingAlert synthesizeAlert(
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
