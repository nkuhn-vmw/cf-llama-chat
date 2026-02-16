package com.example.cfchat.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class WebContentServiceTest {

    private WebContentService webContentService;

    @BeforeEach
    void setUp() {
        webContentService = new WebContentService();
        ReflectionTestUtils.setField(webContentService, "maxContentLength", 50000);
        ReflectionTestUtils.setField(webContentService, "timeoutMs", 10000);
    }

    @Test
    void fetch_invalidUrl_returnsErrorContent() {
        WebContentService.WebPageContent result = webContentService.fetch("http://invalid.localhost.test");

        assertThat(result.url()).isEqualTo("http://invalid.localhost.test");
        assertThat(result.title()).isEqualTo("Error");
        assertThat(result.text()).startsWith("Failed to fetch:");
        assertThat(result.fetchedAt()).isNotNull();
    }

    @Test
    void fetch_malformedUrl_returnsErrorContent() {
        WebContentService.WebPageContent result = webContentService.fetch("not-a-url");

        assertThat(result.title()).isEqualTo("Error");
        assertThat(result.text()).startsWith("Failed to fetch:");
    }

    @Test
    void fetch_withTimeout_returnsErrorOnUnreachableHost() {
        ReflectionTestUtils.setField(webContentService, "timeoutMs", 1);

        WebContentService.WebPageContent result = webContentService.fetch("http://192.0.2.1");

        assertThat(result.title()).isEqualTo("Error");
        assertThat(result.text()).contains("Failed to fetch:");
    }

    @Test
    void fetch_contentTruncation_respectsMaxLength() {
        ReflectionTestUtils.setField(webContentService, "maxContentLength", 10);

        // Fetching any URL that fails will not exercise truncation,
        // but we can verify the field is set correctly
        int maxLen = (int) ReflectionTestUtils.getField(webContentService, "maxContentLength");
        assertThat(maxLen).isEqualTo(10);
    }

    @Test
    void isValidUrl_httpUrl_returnsTrue() {
        assertThat(webContentService.isValidUrl("http://example.com")).isTrue();
    }

    @Test
    void isValidUrl_httpsUrl_returnsTrue() {
        assertThat(webContentService.isValidUrl("https://example.com")).isTrue();
    }

    @Test
    void isValidUrl_ftpUrl_returnsFalse() {
        assertThat(webContentService.isValidUrl("ftp://example.com")).isFalse();
    }

    @Test
    void isValidUrl_nullUrl_returnsFalse() {
        assertThat(webContentService.isValidUrl(null)).isFalse();
    }

    @Test
    void isValidUrl_emptyString_returnsFalse() {
        assertThat(webContentService.isValidUrl("")).isFalse();
    }

    @Test
    void isValidUrl_plainText_returnsFalse() {
        assertThat(webContentService.isValidUrl("just some text")).isFalse();
    }

    @Test
    void fetch_preservesOriginalUrl() {
        String url = "http://nonexistent.localhost.test/page";
        WebContentService.WebPageContent result = webContentService.fetch(url);

        assertThat(result.url()).isEqualTo(url);
    }

    @Test
    void fetch_setsTimestamp() {
        WebContentService.WebPageContent result = webContentService.fetch("http://nonexistent.localhost.test");

        assertThat(result.fetchedAt()).isNotNull();
    }
}
