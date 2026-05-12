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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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

            String formatted = formatUsefulResults(combinedResults, tickerSymbol);
            if (formatted == null || formatted.isBlank()) {
                return "No relevant company, sector, or market news found for " + tickerSymbol;
            }
            return formatted;

        } catch (Exception e) {
            log.error("Failed to execute web search for {}", tickerSymbol, e);
            return "Error searching for company/sector/market news: " + e.getMessage();
        }
    }

    private String formatUsefulResults(List<WebSearchOrganicResult> results, String tickerSymbol) {
        if (results == null || results.isEmpty()) {
            return "";
        }

        Set<String> bullets = new LinkedHashSet<>();
        for (WebSearchOrganicResult result : results) {
            if (result == null || !isUsefulResult(result, tickerSymbol)) {
                continue;
            }

            String title = cleanText(result.title());
            String summary = chooseSummary(result);
            if (summary == null || summary.isBlank()) {
                continue;
            }

            if (title == null || title.isBlank()) {
                bullets.add(summary);
            } else {
                bullets.add(title + ": " + summary);
            }

            if (bullets.size() >= 2) {
                break;
            }
        }

        return String.join("\n", bullets);
    }

    private boolean isUsefulResult(WebSearchOrganicResult result, String tickerSymbol) {
        String title = lower(result.title());
        String content = lower(result.content());
        String snippet = lower(result.snippet());
        String url = lower(cleanUrl(result.url() != null ? result.url().toString() : ""));
        String ticker = tickerSymbol.toLowerCase(Locale.ROOT);
        String combinedText = (title + " " + content + " " + snippet).trim();

        if (combinedText.isBlank()) {
            return false;
        }

        if ((content == null || content.isBlank() || "null".equals(content.trim()))
                && (snippet == null || snippet.isBlank() || "null".equals(snippet.trim()))) {
            return false;
        }

        if (containsAny(title, "stock price", "price quote", "quote & news", "stock updates - page", "google finance", "robinhood", "cnn markets", "stock quote", "forecast")
                || containsAny(url, "/quote/", "/stocks/", "/markets/stocks/", "/finance/quote/")) {
            return false;
        }

        boolean mentionsTickerOrCompany = combinedText.contains(ticker)
                || containsAny(combinedText, "block inc", "block, inc", "tesla", "occidental petroleum", "oxy", "square");

        boolean hasEventLanguage = containsAny(combinedText,
                "earnings", "guidance", "revenue", "profit", "forecast", "analyst", "upgrade", "downgrade",
                "acquisition", "merger", "partnership", "lawsuit", "investigation", "sec", "launch", "catalyst",
                "after-hours", "premarket", "extended trading");

        return mentionsTickerOrCompany && hasEventLanguage;
    }

    private String chooseSummary(WebSearchOrganicResult result) {
        String content = cleanText(result.content());
        if (content != null && !content.isBlank() && !"null".equalsIgnoreCase(content)) {
            return trimSentence(content, 220);
        }
        String snippet = cleanText(result.snippet());
        if (snippet != null && !snippet.isBlank() && !"null".equalsIgnoreCase(snippet)) {
            return trimSentence(snippet, 220);
        }
        return "";
    }

    private String trimSentence(String text, int maxLen) {
        if (text == null) return "";
        String trimmed = text.trim().replaceAll("\\s+", " ");
        if (trimmed.length() <= maxLen) return trimmed;
        return trimmed.substring(0, maxLen - 1).trim() + "…";
    }

    private String cleanText(String text) {
        if (text == null) return "";
        return text.replaceAll("\\s+", " ").trim();
    }

    private String cleanUrl(String url) {
        if (url == null || url.isBlank()) return "";
        return URLDecoder.decode(url, StandardCharsets.UTF_8);
    }

    private boolean containsAny(String text, String... needles) {
        if (text == null) return false;
        for (String needle : needles) {
            if (text.contains(needle)) return true;
        }
        return false;
    }

    private String lower(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    private List<String> buildQueries(String tickerSymbol) {
        String upper = tickerSymbol.toUpperCase(Locale.ROOT);
        List<String> queries = new ArrayList<>();

        switch (upper) {
            case "OXY" -> {
                queries.add("Occidental Petroleum earnings guidance analyst news");
                queries.add("Occidental Petroleum after hours move catalyst");
                queries.add("Occidental Petroleum acquisition lawsuit investigation oil news");
                queries.add("oil energy sector catalyst affecting Occidental Petroleum today");
            }
            case "TSLA" -> {
                queries.add("Tesla earnings guidance analyst news");
                queries.add("Tesla after hours move catalyst");
                queries.add("Tesla launch delivery investigation lawsuit news");
                queries.add("EV sector catalyst affecting Tesla today");
            }
            case "XYZ", "SQ" -> {
                queries.add("Block Inc earnings guidance payments news");
                queries.add("Block Inc after hours move catalyst");
                queries.add("Block Inc analyst upgrade downgrade news");
                queries.add("Block Inc acquisition lawsuit investigation fintech news");
                queries.add("fintech payments sector catalyst affecting Block today");
            }
            default -> {
                queries.add(upper + " earnings guidance analyst news");
                queries.add(upper + " after hours move catalyst");
                queries.add(upper + " acquisition lawsuit investigation news");
                queries.add(upper + " industry sector catalyst today");
            }
        }

        return queries;
    }
}
