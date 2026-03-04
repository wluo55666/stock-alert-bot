package com.weiluo.marketalert.service;

import com.weiluo.marketalert.model.MarketTrade;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseBar;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Component
public class MarketTradeAggregator {

    public record SymbolBar(String symbol, Bar bar) {
    }

    /**
     * Aggregates a stream of market trades into a stream of ta4j Bars. Groups
     * trades by the specified duration. Note: This groups strictly by wall-clock
     * time passing. If no trades occur in a window, no bar is emitted for that
     * window.
     *
     * @param tradeStream the incoming stream of trades
     * @param barDuration the duration of each bar (e.g., 1 minute)
     * @return a stream of aggregated Bars
     */
    public Flux<SymbolBar> aggregateToBars(Flux<MarketTrade> tradeStream, Duration barDuration) {
        long barMillis = barDuration.toMillis();
        return tradeStream.groupBy(MarketTrade::symbol)
                .flatMap(groupedStream -> groupedStream.windowUntilChanged(trade -> trade.timestamp() / barMillis)
                        .flatMap(window -> reduceToBar(window, barDuration, groupedStream.key())));
    }

    private Mono<SymbolBar> reduceToBar(Flux<MarketTrade> window, Duration barDuration, String symbol) {
        return window.collectList().filter(trades -> !trades.isEmpty()).map(trades -> {
            double open = trades.get(0).price();
            double close = trades.get(trades.size() - 1).price();
            double high = Double.MIN_VALUE;
            double low = Double.MAX_VALUE;
            double volume = 0; // If volume is not in MarketTrade, we can use 0 or trade count

            for (MarketTrade trade : trades) {
                double price = trade.price();
                high = Math.max(high, price);
                low = Math.min(low, price);
            }

            // Group by bucket to get logical end time
            long bucketId = trades.get(0).timestamp() / barDuration.toMillis();
            long endMillis = (bucketId + 1) * barDuration.toMillis();
            ZonedDateTime endTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(endMillis), ZoneId.systemDefault());

            return new SymbolBar(symbol, new BaseBar(barDuration, endTime, open, high, low, close, volume));
        });
    }
}
