package com.weiluo.marketalert.service;

public record StructuredTradingAlert(
        String summary,
        String whyItMatters,
        String nextWatch,
        String invalidation,
        String newsCatalyst,
        boolean newsCheckAttempted,
        boolean newsFound
) {
}
