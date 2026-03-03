package com.weiluo.marketalert.service;

import com.weiluo.marketalert.model.MarketTrade;
import com.weiluo.marketalert.service.MarketTradeAggregator.SymbolBar;
import org.junit.jupiter.api.Test;
import org.ta4j.core.Bar;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketTradeAggregatorTest {

    @Test
    void testAggregateToBars() {
        MarketTradeAggregator aggregator = new MarketTradeAggregator();
        Duration barDuration = Duration.ofSeconds(1);

        long now = System.currentTimeMillis();

        Flux<MarketTrade> trades = Flux.just(new MarketTrade("AAPL", 150.0, now),
                new MarketTrade("AAPL", 155.0, now + 100), new MarketTrade("AAPL", 149.0, now + 500),
                new MarketTrade("AAPL", 152.0, now + 900));

        Flux<SymbolBar> barStream = aggregator.aggregateToBars(trades, barDuration);

        StepVerifier.create(barStream).assertNext(symbolBar -> {
            assertEquals("AAPL", symbolBar.symbol());
            Bar bar = symbolBar.bar();
            assertEquals(150.0, bar.getOpenPrice().doubleValue());
            assertEquals(155.0, bar.getHighPrice().doubleValue());
            assertEquals(149.0, bar.getLowPrice().doubleValue());
            assertEquals(152.0, bar.getClosePrice().doubleValue());
        }).verifyComplete();
    }
}
