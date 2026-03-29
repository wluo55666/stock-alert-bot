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
    void testGetLatestNews() {
        // Arrange
        WebSearchOrganicResult mockResult = new WebSearchOrganicResult(
                "AAPL hits new high",
                URI.create("https://example.com/aapl"),
                "Apple stock reached unprecedented levels today.",
                "Apple stock reached unprecedented levels today."
        );
        WebSearchResults results = new WebSearchResults(dev.langchain4j.web.search.WebSearchInformationResult.from(1L), List.of(mockResult));
        
        when(webSearchEngine.search(anyString())).thenReturn(results);

        // Act
        String news = marketNewsTool.getLatestNews("AAPL");

        // Assert
        assertTrue(news.contains("AAPL hits new high"));
        assertTrue(news.contains("https://example.com/aapl"));
        assertTrue(news.contains("Levels today") || news.contains("unprecedented"));
    }
}
