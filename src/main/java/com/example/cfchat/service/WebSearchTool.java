package com.example.cfchat.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class WebSearchTool implements Function<WebSearchTool.Request, WebSearchTool.Response> {

    private final WebSearchService webSearchService;

    public record Request(
            @JsonProperty(required = true) String query,
            @JsonProperty(defaultValue = "5") int maxResults) {}

    public record Response(List<WebSearchService.SearchResult> results) {}

    @Override
    public Response apply(Request request) {
        int max = request.maxResults() > 0 ? request.maxResults() : 5;
        return new Response(webSearchService.search(request.query(), max));
    }
}
