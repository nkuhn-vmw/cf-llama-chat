package com.example.cfchat.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrganizationTest {

    @Test
    void builder_defaults_areSet() {
        Organization org = Organization.builder().name("Test Org").build();
        assertThat(org.getPrimaryColor()).isEqualTo("#10a37f");
        assertThat(org.getSecondaryColor()).isEqualTo("#1a1a1a");
        assertThat(org.getDefaultTheme()).isEqualTo(Organization.Theme.DARK);
        assertThat(org.getActive()).isTrue();
        assertThat(org.getHeaderText()).isEqualTo("Chat");
        assertThat(org.getBorderRadius()).isEqualTo("12px");
    }

    @Test
    void onCreate_generatesSlugFromName() {
        Organization org = Organization.builder().name("My Test Org").build();
        org.onCreate();
        assertThat(org.getSlug()).isEqualTo("my-test-org");
    }

    @Test
    void onCreate_specialCharsInName_generatesCleanSlug() {
        Organization org = Organization.builder().name("Test @ Org #1!").build();
        org.onCreate();
        assertThat(org.getSlug()).matches("[a-z0-9-]+");
    }

    @Test
    void onCreate_existingSlug_keepsExistingSlug() {
        Organization org = Organization.builder().name("Test").slug("custom-slug").build();
        org.onCreate();
        assertThat(org.getSlug()).isEqualTo("custom-slug");
    }

    @Test
    void onCreate_setsTimestamps() {
        Organization org = Organization.builder().name("Test").build();
        org.onCreate();
        assertThat(org.getCreatedAt()).isNotNull();
        assertThat(org.getUpdatedAt()).isNotNull();
    }

    @Test
    void theme_values_containsExpected() {
        assertThat(Organization.Theme.values()).containsExactly(
                Organization.Theme.LIGHT,
                Organization.Theme.DARK
        );
    }
}
