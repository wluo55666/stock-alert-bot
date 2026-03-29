package com.weiluo.marketalert.service;

import com.weiluo.marketalert.config.AppProperties;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchResults;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.stream.Collectors;

@Component
public class MarketNewsTool {

    private static final Logger log = LoggerFactory.getLogger(MarketNewsTool.class);
    private final WebSearchEngine webSearchEngine;

    @Autowired
    public MarketNewsTool(AppProperties properties) {
        String apiKey = properties.ai() != null ? properties.ai().tavilyApiKey() : null;
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Tavily API key is not configured. Web search tool will not have live internet access.");
            this.webSearchEngine = null;
        } else {
            this.webSearchEngine = TavilyWebSearchEngine.builder()
                .apiKey(apiKey)
                .build();
        }
    }

    // Constructor for testing
    MarketNewsTool(WebSearchEngine webSearchEngine) {
        this.webSearchEngine = webSearchEngine;
    }

    @Tool("Searches the web for the latest financial news and fundamental updates for a given stock ticker symbol.")
    public String getLatestNews(String tickerSymbol) {
        log.info("AI requested web news search for {}", tickerSymbol);
        
        if (webSearchEngine == null) {
            return "Web Search Engine is not configured. Please proceed without live fundamental news.";
        }
        
        try {
            WebSearchResults results = webSearchEngine.search(tickerSymbol + " stock latest news update");
            if (results.results() == null || results.results().isEmpty()) {
                return "No recent news found for " + tickerSymbol;
            }
            
            return results.results().stream()
                .limit(3)
                .map(result -> "Title: " + result.title() + "\nSource: " + result.url() + "\nSummary: " + result.content())
                .collect(Collectors.joining("\n\n"));
                
        } catch (Exception e) {
            log.error("Failed to execute web search for {}", tickerSymbol, e);
            return "Error searching for news: " + e.getMessage();
        }
    }
}
