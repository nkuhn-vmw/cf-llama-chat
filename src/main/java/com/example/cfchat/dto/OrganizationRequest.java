package com.example.cfchat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationRequest {
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
    private Boolean active;
}
