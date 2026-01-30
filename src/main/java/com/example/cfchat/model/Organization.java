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

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (slug == null || slug.isBlank()) {
            slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "-");
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum Theme {
        LIGHT,
        DARK
    }
}
