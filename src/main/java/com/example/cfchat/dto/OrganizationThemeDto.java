package com.example.cfchat.dto;

import com.example.cfchat.model.Organization;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight DTO for organization theme/branding sent to the frontend.
 * Contains only the visual customization properties.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationThemeDto {
    private String name;
    private String slug;
    private String logoUrl;
    private String faviconUrl;
    private String welcomeMessage;
    private String primaryColor;
    private String secondaryColor;
    private String accentColor;
    private String textColor;
    private String backgroundColor;
    private String sidebarColor;
    private String headerText;
    private String fontFamily;
    private String borderRadius;
    private String defaultTheme;
    private String customCss;

    public static OrganizationThemeDto fromEntity(Organization org) {
        if (org == null) {
            return createDefaultTheme();
        }
        return OrganizationThemeDto.builder()
                .name(org.getName())
                .slug(org.getSlug())
                .logoUrl(org.getLogoUrl())
                .faviconUrl(org.getFaviconUrl())
                .welcomeMessage(org.getWelcomeMessage())
                .primaryColor(org.getPrimaryColor())
                .secondaryColor(org.getSecondaryColor())
                .accentColor(org.getAccentColor())
                .textColor(org.getTextColor())
                .backgroundColor(org.getBackgroundColor())
                .sidebarColor(org.getSidebarColor())
                .headerText(org.getHeaderText())
                .fontFamily(org.getFontFamily())
                .borderRadius(org.getBorderRadius())
                .defaultTheme(org.getDefaultTheme() != null ? org.getDefaultTheme().name() : "DARK")
                .customCss(org.getCustomCss())
                .build();
    }

    public static OrganizationThemeDto createDefaultTheme() {
        return OrganizationThemeDto.builder()
                .name("Default")
                .slug("default")
                .primaryColor("#10a37f")
                .secondaryColor("#1a1a1a")
                .accentColor("#10a37f")
                .textColor("#ffffff")
                .backgroundColor("#0f0f0f")
                .sidebarColor("#0f0f0f")
                .headerText("Chat")
                .fontFamily("-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif")
                .borderRadius("12px")
                .defaultTheme("DARK")
                .build();
    }
}
