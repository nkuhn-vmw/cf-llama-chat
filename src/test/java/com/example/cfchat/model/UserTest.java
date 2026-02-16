package com.example.cfchat.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void builder_defaultRole_isUser() {
        User user = User.builder().username("test").build();
        assertThat(user.getRole()).isEqualTo(User.UserRole.USER);
    }

    @Test
    void builder_defaultAuthProvider_isLocal() {
        User user = User.builder().username("test").build();
        assertThat(user.getAuthProvider()).isEqualTo(User.AuthProvider.LOCAL);
    }

    @Test
    void builder_defaultOrgRole_isMember() {
        User user = User.builder().username("test").build();
        assertThat(user.getOrganizationRole()).isEqualTo(User.OrganizationRole.MEMBER);
    }

    @Test
    void userRole_values_containsExpected() {
        assertThat(User.UserRole.values()).containsExactly(User.UserRole.USER, User.UserRole.ADMIN);
    }

    @Test
    void authProvider_values_containsExpected() {
        assertThat(User.AuthProvider.values()).containsExactly(
                User.AuthProvider.LOCAL, User.AuthProvider.SSO,
                User.AuthProvider.LDAP, User.AuthProvider.SCIM);
    }

    @Test
    void organizationRole_values_containsExpected() {
        assertThat(User.OrganizationRole.values()).containsExactly(
                User.OrganizationRole.MEMBER,
                User.OrganizationRole.ADMIN,
                User.OrganizationRole.OWNER
        );
    }

    @Test
    void onCreate_setsTimestamps() {
        User user = User.builder().username("test").build();
        user.onCreate();
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getLastLoginAt()).isNotNull();
    }
}
