package com.example.cfchat.dto;

import com.example.cfchat.model.Organization;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationDto {
    private UUID id;
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean active;
    private Long memberCount;

    public static OrganizationDto fromEntity(Organization org) {
        return OrganizationDto.builder()
                .id(org.getId())
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
                .createdAt(org.getCreatedAt())
                .updatedAt(org.getUpdatedAt())
                .active(org.getActive())
                .build();
    }

    public static OrganizationDto fromEntityWithMemberCount(Organization org, Long memberCount) {
        OrganizationDto dto = fromEntity(org);
        dto.setMemberCount(memberCount);
        return dto;
    }
}
