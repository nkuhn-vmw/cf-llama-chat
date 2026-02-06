package com.example.cfchat.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "organizations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "favicon_url")
    private String faviconUrl;

    @Column(name = "welcome_message", length = 1000)
    private String welcomeMessage;

    @Column(name = "primary_color")
    @Builder.Default
    private String primaryColor = "#10a37f";

    @Column(name = "secondary_color")
    @Builder.Default
    private String secondaryColor = "#1a1a1a";

    @Column(name = "accent_color")
    @Builder.Default
    private String accentColor = "#10a37f";

    @Column(name = "text_color")
    @Builder.Default
    private String textColor = "#ffffff";

    @Column(name = "background_color")
    @Builder.Default
    private String backgroundColor = "#0f0f0f";

    @Column(name = "sidebar_color")
    @Builder.Default
    private String sidebarColor = "#0f0f0f";

    @Column(name = "header_text")
    @Builder.Default
    private String headerText = "Chat";

    @Column(name = "font_family")
    @Builder.Default
    private String fontFamily = "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif";

    @Column(name = "border_radius")
    @Builder.Default
    private String borderRadius = "12px";

    @Column(name = "default_theme")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Theme defaultTheme = Theme.DARK;

    @Column(name = "custom_css", length = 10000)
    private String customCss;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    private static final java.util.regex.Pattern SAFE_COLOR = java.util.regex.Pattern.compile(
            "^(#[0-9a-fA-F]{3,8}|[a-zA-Z]{1,30}|rgb\\(\\d{1,3},\\s*\\d{1,3},\\s*\\d{1,3}\\)|rgba\\(\\d{1,3},\\s*\\d{1,3},\\s*\\d{1,3},\\s*[0-9.]+\\))$");
    private static final java.util.regex.Pattern SAFE_URL = java.util.regex.Pattern.compile(
            "^(https?://|/).*$", java.util.regex.Pattern.CASE_INSENSITIVE);

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (slug == null || slug.isBlank()) {
            slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "-");
        }
        sanitizeThemeFields();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        sanitizeThemeFields();
    }

    private void sanitizeThemeFields() {
        primaryColor = sanitizeColor(primaryColor, "#10a37f");
        secondaryColor = sanitizeColor(secondaryColor, "#1a1a1a");
        accentColor = sanitizeColor(accentColor, "#10a37f");
        textColor = sanitizeColor(textColor, "#ffffff");
        backgroundColor = sanitizeColor(backgroundColor, "#0f0f0f");
        sidebarColor = sanitizeColor(sidebarColor, "#0f0f0f");
        logoUrl = sanitizeUrl(logoUrl);
        faviconUrl = sanitizeUrl(faviconUrl);
        customCss = sanitizeCss(customCss);
    }

    private static String sanitizeColor(String color, String defaultColor) {
        if (color == null || color.isBlank()) return defaultColor;
        return SAFE_COLOR.matcher(color.trim()).matches() ? color.trim() : defaultColor;
    }

    private static String sanitizeUrl(String url) {
        if (url == null || url.isBlank()) return null;
        return SAFE_URL.matcher(url.trim()).matches() ? url.trim() : null;
    }

    private static String sanitizeCss(String css) {
        if (css == null || css.isBlank()) return null;
        // Strip any HTML tags (prevents </style><script> breakout)
        String sanitized = css.replaceAll("<[^>]*>", "");
        // Strip dangerous CSS functions
        sanitized = sanitized.replaceAll("(?i)(expression|javascript|vbscript|@import)\\s*\\(", "blocked(");
        return sanitized;
    }

    public enum Theme {
        LIGHT,
        DARK
    }
}
