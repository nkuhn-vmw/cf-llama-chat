package com.example.cfchat.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for Organization entity sanitization methods.
 * Verifies security-critical sanitization of colors, URLs, and CSS.
 */
class OrganizationSanitizationTest {

    @Test
    void sanitizeThemeFields_validHexColors_preserved() {
        Organization org = Organization.builder()
                .name("Test")
                .primaryColor("#ff0000")
                .secondaryColor("#00ff00")
                .accentColor("#0000ff")
                .textColor("#fff")
                .backgroundColor("#000")
                .sidebarColor("#123456")
                .build();

        org.onCreate();

        assertThat(org.getPrimaryColor()).isEqualTo("#ff0000");
        assertThat(org.getSecondaryColor()).isEqualTo("#00ff00");
        assertThat(org.getAccentColor()).isEqualTo("#0000ff");
        assertThat(org.getTextColor()).isEqualTo("#fff");
    }

    @Test
    void sanitizeThemeFields_invalidColors_resetToDefault() {
        Organization org = Organization.builder()
                .name("Test")
                .primaryColor("javascript:alert(1)")
                .secondaryColor("<script>")
                .accentColor("expression(alert(1))")
                .build();

        org.onCreate();

        assertThat(org.getPrimaryColor()).isEqualTo("#10a37f"); // default
        assertThat(org.getSecondaryColor()).isEqualTo("#1a1a1a"); // default
        assertThat(org.getAccentColor()).isEqualTo("#10a37f"); // default
    }

    @Test
    void sanitizeThemeFields_namedColors_preserved() {
        Organization org = Organization.builder()
                .name("Test")
                .primaryColor("red")
                .secondaryColor("blue")
                .build();

        org.onCreate();

        assertThat(org.getPrimaryColor()).isEqualTo("red");
        assertThat(org.getSecondaryColor()).isEqualTo("blue");
    }

    @Test
    void sanitizeThemeFields_rgbColors_preserved() {
        Organization org = Organization.builder()
                .name("Test")
                .primaryColor("rgb(255, 0, 0)")
                .secondaryColor("rgba(0, 255, 0, 0.5)")
                .build();

        org.onCreate();

        assertThat(org.getPrimaryColor()).isEqualTo("rgb(255, 0, 0)");
        assertThat(org.getSecondaryColor()).isEqualTo("rgba(0, 255, 0, 0.5)");
    }

    @Test
    void sanitizeUrl_validHttpsUrl_preserved() {
        Organization org = Organization.builder()
                .name("Test")
                .logoUrl("https://example.com/logo.png")
                .faviconUrl("https://example.com/favicon.ico")
                .build();

        org.onCreate();

        assertThat(org.getLogoUrl()).isEqualTo("https://example.com/logo.png");
        assertThat(org.getFaviconUrl()).isEqualTo("https://example.com/favicon.ico");
    }

    @Test
    void sanitizeUrl_validRelativePath_preserved() {
        Organization org = Organization.builder()
                .name("Test")
                .logoUrl("/images/logo.png")
                .build();

        org.onCreate();

        assertThat(org.getLogoUrl()).isEqualTo("/images/logo.png");
    }

    @Test
    void sanitizeUrl_javascriptUrl_nullified() {
        Organization org = Organization.builder()
                .name("Test")
                .logoUrl("javascript:alert(1)")
                .faviconUrl("data:text/html,<script>alert(1)</script>")
                .build();

        org.onCreate();

        assertThat(org.getLogoUrl()).isNull();
        assertThat(org.getFaviconUrl()).isNull();
    }

    @Test
    void sanitizeCss_validCss_preserved() {
        Organization org = Organization.builder()
                .name("Test")
                .customCss(".header { color: red; background: #fff; }")
                .build();

        org.onCreate();

        assertThat(org.getCustomCss()).isEqualTo(".header { color: red; background: #fff; }");
    }

    @Test
    void sanitizeCss_htmlTags_stripped() {
        Organization org = Organization.builder()
                .name("Test")
                .customCss("</style><script>alert(1)</script><style>")
                .build();

        org.onCreate();

        assertThat(org.getCustomCss()).doesNotContain("<script>");
        assertThat(org.getCustomCss()).doesNotContain("</style>");
        assertThat(org.getCustomCss()).doesNotContain("<style>");
    }

    @Test
    void sanitizeCss_dangerousFunctions_blocked() {
        Organization org = Organization.builder()
                .name("Test")
                .customCss("body { background: expression(alert(1)); }")
                .build();

        org.onCreate();

        assertThat(org.getCustomCss()).doesNotContain("expression(");
        assertThat(org.getCustomCss()).contains("blocked(");
    }

    @Test
    void sanitizeCss_importBlocked() {
        Organization org = Organization.builder()
                .name("Test")
                .customCss("@import (\"https://evil.com/steal.css\")")
                .build();

        org.onCreate();

        assertThat(org.getCustomCss()).doesNotContain("@import (");
    }

    @Test
    void sanitizeCss_nullCss_returnsNull() {
        Organization org = Organization.builder()
                .name("Test")
                .customCss(null)
                .build();

        org.onCreate();

        assertThat(org.getCustomCss()).isNull();
    }

    @Test
    void sanitizeCss_blankCss_returnsNull() {
        Organization org = Organization.builder()
                .name("Test")
                .customCss("   ")
                .build();

        org.onCreate();

        assertThat(org.getCustomCss()).isNull();
    }

    @Test
    void onUpdate_alsoCalls_sanitizeThemeFields() {
        Organization org = Organization.builder()
                .name("Test")
                .primaryColor("invalid-color-value!!!")
                .build();

        org.onUpdate();

        assertThat(org.getPrimaryColor()).isEqualTo("#10a37f"); // sanitized to default
        assertThat(org.getUpdatedAt()).isNotNull();
    }
}
