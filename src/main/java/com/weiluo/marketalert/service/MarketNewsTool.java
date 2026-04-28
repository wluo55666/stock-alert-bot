package com.weiluo.marketalert.service;

import com.weiluo.marketalert.config.AppProperties;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchResults;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    MarketNewsTool(WebSearchEngine webSearchEngine) {
        this.webSearchEngine = webSearchEngine;
    }

    @Tool("Searches the web for recent company, sector, industry, and market-theme news that could help explain a stock move.")
    public String getLatestNews(String tickerSymbol) {
        log.info("AI requested web news search for {}", tickerSymbol);

        if (webSearchEngine == null) {
            return "Web Search Engine is not configured. Please proceed without live market/news context.";
        }

        try {
            List<String> queries = buildQueries(tickerSymbol);
            List<WebSearchOrganicResult> combinedResults = new ArrayList<>();

            for (String query : queries) {
                WebSearchResults results = webSearchEngine.search(query);
                if (results != null && results.results() != null) {
                    combinedResults.addAll(results.results());
                }
            }

            if (combinedResults.isEmpty()) {
                return "No relevant company, sector, or market news found for " + tickerSymbol;
            }

            return combinedResults.stream()
                    .distinct()
                    .limit(5)
                    .map(result -> "Title: " + result.title() + "\nSource: " + result.url() + "\nSummary: " + result.content())
                    .collect(Collectors.joining("\n\n"));

        } catch (Exception e) {
            log.error("Failed to execute web search for {}", tickerSymbol, e);
            return "Error searching for company/sector/market news: " + e.getMessage();
        }
    }

    private List<String> buildQueries(String tickerSymbol) {
        String upper = tickerSymbol.toUpperCase(Locale.ROOT);
        List<String> queries = new ArrayList<>();

        queries.add(upper + " stock latest news update");

        switch (upper) {
            case "OXY" -> {
                queries.add("Occidental Petroleum latest news oil energy sector crude market today");
                queries.add("oil prices energy sector news today OXY catalyst");
            }
            case "TSLA" -> {
                queries.add("Tesla latest news EV sector market sentiment today");
                queries.add("EV sector news today Tesla catalyst auto tech market");
            }
            case "XYZ", "SQ" -> {
                queries.add("Block Inc latest news fintech payments sector today");
                queries.add("fintech payments sector news today Block stock catalyst");
            }
            default -> queries.add(upper + " industry sector market trend news today");
        }

        queries.add(upper + " sector trend market sentiment today");
        return queries;
    }
}
