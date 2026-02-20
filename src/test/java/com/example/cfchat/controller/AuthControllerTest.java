package com.example.cfchat.controller;

import com.example.cfchat.auth.AuthController;
import com.example.cfchat.auth.SecurityConfig;
import com.example.cfchat.auth.UserService;
import com.example.cfchat.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private SecurityConfig securityConfig;

    @MockBean
    private com.example.cfchat.service.RateLimitService rateLimitService;

    @Test
    void getAuthProvider_returnsProviderInfo() throws Exception {
        when(securityConfig.isSsoConfigured()).thenReturn(false);
        when(securityConfig.isInvitationRequired()).thenReturn(false);

        mockMvc.perform(get("/auth/provider"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ssoEnabled").value(false))
                .andExpect(jsonPath("$.registrationEnabled").value(true))
                .andExpect(jsonPath("$.invitationRequired").value(false));
    }

    @Test
    void getAuthProvider_withSso_returnsSsoUrl() throws Exception {
        when(securityConfig.isSsoConfigured()).thenReturn(true);
        when(securityConfig.isInvitationRequired()).thenReturn(false);

        mockMvc.perform(get("/auth/provider"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ssoEnabled").value(true))
                .andExpect(jsonPath("$.ssoUrl").value("/oauth2/authorization/sso"));
    }

    @Test
    void register_validRequest_returnsSuccess() throws Exception {
        User newUser = User.builder()
                .username("testuser")
                .role(User.UserRole.USER)
                .build();
        when(userService.registerUser("testuser", "Password1", "test@test.com", "Test"))
                .thenReturn(newUser);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "testuser",
                                "password", "Password1",
                                "email", "test@test.com",
                                "displayName", "Test"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.username").value("testuser"));
    }

    @Test
    void register_missingUsername_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "password", "password123"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Username is required"));
    }

    @Test
    void register_shortPassword_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "testuser",
                                "password", "short"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Password must be at least 8 characters with uppercase, lowercase, and a number"));
    }

    @Test
    void register_invalidUsernameFormat_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "a@b",
                                "password", "password123"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void register_tooShortUsername_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "ab",
                                "password", "password123"
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_invitationRequired_missingCode_returnsBadRequest() throws Exception {
        when(securityConfig.isInvitationRequired()).thenReturn(true);
        when(securityConfig.getInvitationCode()).thenReturn("secret123");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "testuser",
                                "password", "Password1"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid invitation code"));
    }

    @Test
    void register_invitationRequired_correctCode_succeeds() throws Exception {
        when(securityConfig.isInvitationRequired()).thenReturn(true);
        when(securityConfig.getInvitationCode()).thenReturn("secret123");
        User newUser = User.builder().username("testuser").role(User.UserRole.USER).build();
        when(userService.registerUser(anyString(), anyString(), anyString(), anyString())).thenReturn(newUser);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "testuser",
                                "password", "Password1",
                                "email", "test@test.com",
                                "displayName", "Test",
                                "invitationCode", "secret123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void checkUsername_available_returnsTrue() throws Exception {
        when(userService.isUsernameAvailable("available")).thenReturn(true);

        mockMvc.perform(get("/auth/check-username").param("username", "available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void checkUsername_taken_returnsFalse() throws Exception {
        when(userService.isUsernameAvailable("taken")).thenReturn(false);

        mockMvc.perform(get("/auth/check-username").param("username", "taken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void getAuthStatus_authenticated_returnsUserInfo() throws Exception {
        User user = User.builder()
                .username("testuser")
                .displayName("Test User")
                .email("test@test.com")
                .role(User.UserRole.USER)
                .authProvider(User.AuthProvider.LOCAL)
                .build();
        when(userService.extractUsername(org.mockito.ArgumentMatchers.any())).thenReturn("testuser");
        when(userService.getCurrentUser()).thenReturn(Optional.of(user));

        mockMvc.perform(get("/auth/status"))
                .andExpect(status().isOk());
    }

    @Test
    void changePassword_shortNewPassword_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "old",
                                "newPassword", "short"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("New password must be at least 8 characters with uppercase, lowercase, and a number"));
    }

    @Test
    void changePassword_valid_succeeds() throws Exception {
        User user = User.builder()
                .username("testuser")
                .authProvider(User.AuthProvider.LOCAL)
                .build();
        when(userService.getCurrentUser()).thenReturn(Optional.of(user));
        when(userService.canChangePassword(user)).thenReturn(true);
        when(userService.changePassword("oldpass", "Newpass123")).thenReturn(true);

        mockMvc.perform(post("/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "oldpass",
                                "newPassword", "Newpass123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void changePassword_ssoUser_returnsBadRequest() throws Exception {
        User user = User.builder()
                .username("ssouser")
                .authProvider(User.AuthProvider.SSO)
                .build();
        when(userService.getCurrentUser()).thenReturn(Optional.of(user));
        when(userService.canChangePassword(user)).thenReturn(false);

        mockMvc.perform(post("/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "old",
                                "newPassword", "Newpass123"
                        ))))
                .andExpect(status().isBadRequest());
    }
}
