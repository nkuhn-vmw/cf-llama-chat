package com.example.cfchat.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class FetchUrlTool implements Function<FetchUrlTool.Request, FetchUrlTool.Response> {

    private final WebContentService webContentService;

    public record Request(@JsonProperty(required = true) String url) {}

    public record Response(String title, String content, String url) {}

    @Override
    public Response apply(Request request) {
        WebContentService.WebPageContent page = webContentService.fetch(request.url());
        return new Response(page.title(), page.text(), page.url());
    }
}
