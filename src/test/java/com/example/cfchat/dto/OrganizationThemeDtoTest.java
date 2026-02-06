package com.example.cfchat.dto;

import com.example.cfchat.model.Organization;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrganizationThemeDtoTest {

    @Test
    void createDefaultTheme_returnsCorrectDefaults() {
        OrganizationThemeDto theme = OrganizationThemeDto.createDefaultTheme();

        assertEquals("Default", theme.getName());
        assertEquals("default", theme.getSlug());
        assertEquals("#10a37f", theme.getPrimaryColor());
        assertEquals("DARK", theme.getDefaultTheme());
    }

    @Test
    void fromEntity_nullOrg_returnsDefault() {
        OrganizationThemeDto theme = OrganizationThemeDto.fromEntity(null);

        assertEquals("Default", theme.getName());
        assertEquals("#10a37f", theme.getPrimaryColor());
    }

    @Test
    void fromEntity_validOrg_mapsAllFields() {
        Organization org = new Organization();
        org.setName("Custom Org");
        org.setSlug("custom-org");
        org.setPrimaryColor("#ff0000");
        org.setHeaderText("Custom Chat");
        org.setDefaultTheme(Organization.Theme.LIGHT);

        OrganizationThemeDto theme = OrganizationThemeDto.fromEntity(org);

        assertEquals("Custom Org", theme.getName());
        assertEquals("#ff0000", theme.getPrimaryColor());
    }

    @Test
    void fromEntity_nullTheme_defaultsToDark() {
        Organization org = new Organization();
        org.setName("Test");
        org.setSlug("test");
        org.setDefaultTheme(null);

        OrganizationThemeDto theme = OrganizationThemeDto.fromEntity(org);

        assertEquals("DARK", theme.getDefaultTheme());
    }
}
