package com.example.cfchat.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@Slf4j
public class WebContentService {

    @Value("${rag.web.max-content-length:50000}")
    private int maxContentLength;

    @Value("${rag.web.timeout-ms:10000}")
    private int timeoutMs;

    public record WebPageContent(String url, String title, String text, Instant fetchedAt) {}

    public WebPageContent fetch(String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .timeout(timeoutMs)
                    .userAgent("CF-Llama-Chat/2.0")
                    .maxBodySize(maxContentLength * 2)
                    .followRedirects(true)
                    .get();

            // Remove non-content elements
            doc.select("script,style,nav,footer,header,aside,iframe,noscript").remove();

            String text = doc.body() != null ? doc.body().text() : "";
            if (text.length() > maxContentLength) {
                text = text.substring(0, maxContentLength);
            }

            return new WebPageContent(url, doc.title(), text, Instant.now());
        } catch (Exception e) {
            log.warn("Failed to fetch URL {}: {}", url, e.getMessage());
            return new WebPageContent(url, "Error", "Failed to fetch: " + e.getMessage(), Instant.now());
        }
    }

    public boolean isValidUrl(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }
}
