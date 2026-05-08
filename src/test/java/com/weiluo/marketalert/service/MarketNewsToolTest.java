package com.weiluo.marketalert.service;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchResults;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketNewsToolTest {

    @Mock
    private WebSearchEngine webSearchEngine;

    private MarketNewsTool marketNewsTool;

    @BeforeEach
    void setUp() {
        marketNewsTool = new MarketNewsTool(webSearchEngine);
    }

    @Test
    void testGetLatestNewsReturnsUsefulCatalystText() {
        WebSearchOrganicResult junkResult = new WebSearchOrganicResult(
                "Block: XYZ Stock Price Quote & News - Robinhood",
                URI.create("https://robinhood.com/us/en/stocks/XYZ/"),
                null,
                null
        );
        WebSearchOrganicResult usefulResult = new WebSearchOrganicResult(
                "Block shares jump after earnings beat",
                URI.create("https://example.com/block-earnings"),
                "Block rose in extended trading after reporting better-than-expected revenue and stronger payments volume.",
                "Block rose in extended trading after reporting better-than-expected revenue and stronger payments volume."
        );
        WebSearchResults results = new WebSearchResults(
                dev.langchain4j.web.search.WebSearchInformationResult.from(2L),
                List.of(junkResult, usefulResult)
        );

        when(webSearchEngine.search(anyString())).thenReturn(results);

        String news = marketNewsTool.getLatestNews("XYZ");

        assertTrue(news.contains("Block shares jump after earnings beat"));
        assertTrue(news.contains("better-than-expected revenue"));
        assertFalse(news.contains("Robinhood"));
        assertFalse(news.contains("Summary: null"));
    }

    @Test
    void testGetLatestNewsFallsBackWhenOnlyJunkResultsExist() {
        WebSearchOrganicResult junkResult = new WebSearchOrganicResult(
                "XYZ Stock Quote Price and Forecast - CNN",
                URI.create("https://www.cnn.com/markets/stocks/XYZ"),
                null,
                null
        );
        WebSearchResults results = new WebSearchResults(
                dev.langchain4j.web.search.WebSearchInformationResult.from(1L),
                List.of(junkResult)
        );

        when(webSearchEngine.search(anyString())).thenReturn(results);

        String news = marketNewsTool.getLatestNews("XYZ");

        assertTrue(news.contains("No relevant company, sector, or market news found for XYZ"));
    }
}
