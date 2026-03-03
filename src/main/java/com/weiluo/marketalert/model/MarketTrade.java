package com.weiluo.marketalert.model;

public record MarketTrade(
                String symbol,
                double price,
                long timestamp) {
}
