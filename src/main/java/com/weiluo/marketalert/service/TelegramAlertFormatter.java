package com.weiluo.marketalert.service;

import org.springframework.stereotype.Component;

@Component
public class TelegramAlertFormatter {

    public String formatTaAlert(String symbol, String signal, int score, StructuredTradingAlert alert) {
        StringBuilder message = new StringBuilder();
        message.append("<b>").append(symbol).append(" — ").append(humanizeSignal(signal)).append("</b>");

        appendBullet(message, safe(alert.summary()));
        appendBullet(message, "<b>Why it matters:</b> " + safe(alert.whyItMatters()));

        if (alert.newsFound() && alert.newsCatalyst() != null && !alert.newsCatalyst().isBlank()) {
            appendBullet(message, "<b>Possible catalyst:</b> " + safe(alert.newsCatalyst()));
        } else if (alert.newsCheckAttempted()) {
            appendBullet(message, "<i>News check:</i> no obvious catalyst found");
        }

        appendBullet(message, "<b>Watch next:</b> " + safe(alert.nextWatch()));
        appendBullet(message, "<b>Invalidation:</b> " + safe(alert.invalidation()));

        if (score >= 4) {
            appendBullet(message, "<i>Signal quality:</i> higher-conviction setup");
        }

        return message.toString();
    }

    public String formatSlidingWindowAlert(String symbol, String signal, int score, StructuredTradingAlert alert) {
        StringBuilder message = new StringBuilder();
        message.append("<b>").append(symbol).append(" — ").append(humanizeSignal(signal)).append("</b>");

        appendBullet(message, safe(alert.summary()));
        appendBullet(message, "<b>Why it matters:</b> " + safe(alert.whyItMatters()));

        if (alert.newsFound() && alert.newsCatalyst() != null && !alert.newsCatalyst().isBlank()) {
            appendBullet(message, "<b>Possible catalyst:</b> " + safe(alert.newsCatalyst()));
        } else if (alert.newsCheckAttempted()) {
            appendBullet(message, "<i>News check:</i> no obvious catalyst found");
        }

        appendBullet(message, "<b>Watch next:</b> " + safe(alert.nextWatch()));
        appendBullet(message, "<b>Invalidation:</b> " + safe(alert.invalidation()));

        if (score >= 4) {
            appendBullet(message, "<i>Signal quality:</i> higher-conviction setup");
        }

        return message.toString();
    }

    private void appendBullet(StringBuilder sb, String text) {
        if (text == null || text.isBlank()) return;
        sb.append("\n• ").append(text);
    }

    private String humanizeSignal(String signal) {
        return signal.replace("📈", "").replace("📉", "").trim();
    }

    private String safe(String text) {
        if (text == null || text.isBlank()) {
            return "n/a";
        }
        return text;
    }
}
