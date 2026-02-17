package com.example.cfchat.service;

import io.micrometer.observation.annotation.Observed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class WebSearchService {

    @Value("${search.enabled:false}")
    private boolean enabled;

    @Value("${search.provider:duckduckgo}")
    private String provider;

    @Value("${search.tavily.api-key:}")
    private String tavilyKey;

    @Value("${search.brave.api-key:}")
    private String braveKey;

    private final RestTemplate restTemplate = new RestTemplate();

    public record SearchResult(String title, String url, String snippet) {}

    public boolean isEnabled() {
        return enabled;
    }

    @Observed(name = "cfllama.web.search",
            contextualName = "web-search",
            lowCardinalityKeyValues = {"operation", "web-search"})
    public List<SearchResult> search(String query, int maxResults) {
        if (!enabled) {
            return List.of();
        }

        maxResults = Math.min(Math.max(maxResults, 1), 10);

        try {
            return switch (provider.toLowerCase()) {
                case "tavily" -> searchTavily(query, maxResults);
                case "brave" -> searchBrave(query, maxResults);
                default -> searchDuckDuckGo(query, maxResults);
            };
        } catch (Exception e) {
            log.error("Web search failed for provider {}: {}", provider, e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<SearchResult> searchDuckDuckGo(String query, int maxResults) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://api.duckduckgo.com/?q=" + encodedQuery + "&format=json&no_html=1&skip_disambig=1";

            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null) return List.of();

            List<SearchResult> results = new ArrayList<>();

            // Abstract text
            String abstractText = (String) response.get("AbstractText");
            String abstractUrl = (String) response.get("AbstractURL");
            if (abstractText != null && !abstractText.isBlank()) {
                results.add(new SearchResult("Summary", abstractUrl != null ? abstractUrl : "", abstractText));
            }

            // Related topics
            List<Map<String, Object>> relatedTopics = (List<Map<String, Object>>) response.get("RelatedTopics");
            if (relatedTopics != null) {
                for (Map<String, Object> topic : relatedTopics) {
                    if (results.size() >= maxResults) break;
                    String text = (String) topic.get("Text");
                    String firstUrl = (String) topic.get("FirstURL");
                    if (text != null && !text.isBlank()) {
                        results.add(new SearchResult(
                                text.length() > 100 ? text.substring(0, 100) + "..." : text,
                                firstUrl != null ? firstUrl : "",
                                text
                        ));
                    }
                }
            }

            return results.subList(0, Math.min(results.size(), maxResults));
        } catch (Exception e) {
            log.warn("DuckDuckGo search failed: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<SearchResult> searchTavily(String query, int maxResults) {
        if (tavilyKey == null || tavilyKey.isBlank()) {
            log.warn("Tavily API key not configured");
            return List.of();
        }

        try {
            Map<String, Object> request = Map.of(
                    "api_key", tavilyKey,
                    "query", query,
                    "max_results", maxResults
            );

            Map<String, Object> response = restTemplate.postForObject(
                    "https://api.tavily.com/search", request, Map.class);

            if (response == null) return List.of();

            List<Map<String, Object>> searchResults = (List<Map<String, Object>>) response.get("results");
            if (searchResults == null) return List.of();

            return searchResults.stream()
                    .limit(maxResults)
                    .map(r -> new SearchResult(
                            (String) r.getOrDefault("title", ""),
                            (String) r.getOrDefault("url", ""),
                            (String) r.getOrDefault("content", "")
                    ))
                    .toList();
        } catch (Exception e) {
            log.warn("Tavily search failed: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<SearchResult> searchBrave(String query, int maxResults) {
        if (braveKey == null || braveKey.isBlank()) {
            log.warn("Brave API key not configured");
            return List.of();
        }

        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://api.search.brave.com/res/v1/web/search?q=" + encodedQuery + "&count=" + maxResults;

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            headers.set("Accept-Encoding", "gzip");
            headers.set("X-Subscription-Token", braveKey);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET,
                    new HttpEntity<>(headers), Map.class);

            Map<String, Object> body = response.getBody();
            if (body == null) return List.of();

            Map<String, Object> web = (Map<String, Object>) body.get("web");
            if (web == null) return List.of();

            List<Map<String, Object>> searchResults = (List<Map<String, Object>>) web.get("results");
            if (searchResults == null) return List.of();

            return searchResults.stream()
                    .limit(maxResults)
                    .map(r -> new SearchResult(
                            (String) r.getOrDefault("title", ""),
                            (String) r.getOrDefault("url", ""),
                            (String) r.getOrDefault("description", "")
                    ))
                    .toList();
        } catch (Exception e) {
            log.warn("Brave search failed: {}", e.getMessage());
            return List.of();
        }
    }
}
