package com.example.cfchat.dto;

import com.example.cfchat.model.Organization;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrganizationDtoTest {

    @Test
    void fromEntity_mapsAllFields() {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        Organization org = Organization.builder()
                .id(id)
                .name("Test Org")
                .slug("test-org")
                .logoUrl("https://example.com/logo.png")
                .faviconUrl("https://example.com/favicon.ico")
                .welcomeMessage("Welcome!")
                .primaryColor("#ff0000")
                .secondaryColor("#00ff00")
                .accentColor("#0000ff")
                .textColor("#ffffff")
                .backgroundColor("#000000")
                .sidebarColor("#111111")
                .headerText("My Chat")
                .fontFamily("Arial")
                .borderRadius("8px")
                .defaultTheme(Organization.Theme.LIGHT)
                .customCss(".test { color: red; }")
                .createdAt(now)
                .updatedAt(now)
                .active(true)
                .build();

        OrganizationDto dto = OrganizationDto.fromEntity(org);

        assertThat(dto.getId()).isEqualTo(id);
        assertThat(dto.getName()).isEqualTo("Test Org");
        assertThat(dto.getSlug()).isEqualTo("test-org");
        assertThat(dto.getLogoUrl()).isEqualTo("https://example.com/logo.png");
        assertThat(dto.getPrimaryColor()).isEqualTo("#ff0000");
        assertThat(dto.getDefaultTheme()).isEqualTo("LIGHT");
        assertThat(dto.getActive()).isTrue();
        assertThat(dto.getMemberCount()).isNull();
    }

    @Test
    void fromEntityWithMemberCount_setsMemberCount() {
        Organization org = Organization.builder()
                .name("Test")
                .slug("test")
                .defaultTheme(Organization.Theme.DARK)
                .build();

        OrganizationDto dto = OrganizationDto.fromEntityWithMemberCount(org, 5L);

        assertThat(dto.getMemberCount()).isEqualTo(5L);
    }

    @Test
    void fromEntity_nullTheme_defaultsToDark() {
        Organization org = Organization.builder()
                .name("Test")
                .slug("test")
                .defaultTheme(null)
                .build();

        OrganizationDto dto = OrganizationDto.fromEntity(org);

        assertThat(dto.getDefaultTheme()).isEqualTo("DARK");
    }
}
