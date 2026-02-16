package com.example.cfchat.controller;

import com.example.cfchat.auth.UserService;
import com.example.cfchat.model.User;
import com.example.cfchat.service.*;
import com.example.cfchat.config.GenAiConfig;
import com.example.cfchat.repository.ConversationRepository;
import com.example.cfchat.repository.EmbeddingMetricRepository;
import com.example.cfchat.repository.OrganizationRepository;
import com.example.cfchat.repository.UsageMetricRepository;
import com.example.cfchat.repository.UserAccessRepository;
import com.example.cfchat.repository.UserDocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security integration tests verifying endpoint access control.
 */
@WebMvcTest(AdminController.class)
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private ConversationService conversationService;

    @MockBean
    private ConversationRepository conversationRepository;

    @MockBean
    private ChatService chatService;

    @MockBean
    private GenAiConfig genAiConfig;

    @MockBean
    private OrganizationRepository organizationRepository;

    @MockBean
    private UserAccessService userAccessService;

    @MockBean
    private DatabaseStatsService databaseStatsService;

    @MockBean
    private UsageMetricRepository usageMetricRepository;

    @MockBean
    private EmbeddingMetricRepository embeddingMetricRepository;

    @MockBean
    private UserAccessRepository userAccessRepository;

    @MockBean
    private UserDocumentRepository userDocumentRepository;


    @MockBean
    private com.example.cfchat.service.RateLimitService rateLimitService;
    // ===== Unauthenticated access to admin endpoints =====

    @Test
    void adminPage_unauthenticated_isUnauthorized() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminApiUsers_unauthenticated_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminApiModels_unauthenticated_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/models"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminApiDatabaseStats_unauthenticated_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/database/stats"))
                .andExpect(status().isUnauthorized());
    }

    // ===== Regular user access to admin endpoints =====

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void adminPage_regularUser_redirectsHome() throws Exception {
        // AdminController checks role and redirects non-admins to /
        when(userService.getCurrentUser()).thenReturn(Optional.of(
                User.builder().id(UUID.randomUUID()).username("user").role(User.UserRole.USER).build()));
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void adminApiUsers_regularUser_isForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void adminApiModels_regularUser_isForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/models"))
                .andExpect(status().isForbidden());
    }

    // ===== Admin user access =====

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminPage_adminUser_isAllowed() throws Exception {
        User admin = User.builder()
                .id(UUID.randomUUID())
                .username("admin")
                .role(User.UserRole.ADMIN)
                .build();
        when(userService.getCurrentUser()).thenReturn(Optional.of(admin));
        when(userService.getAllUsers()).thenReturn(List.of(admin));
        when(userService.getAdminCount()).thenReturn(1L);
        when(chatService.getAvailableModels()).thenReturn(List.of());
        when(organizationRepository.count()).thenReturn(0L);

        mockMvc.perform(get("/admin"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminApiUsers_adminUser_returnsUsers() throws Exception {
        User admin = User.builder()
                .id(UUID.randomUUID())
                .username("admin")
                .role(User.UserRole.ADMIN)
                .build();
        when(userService.getCurrentUser()).thenReturn(Optional.of(admin));
        when(userService.getAllUsers()).thenReturn(List.of(admin));
        when(conversationService.getConversationCountForUser(admin.getId())).thenReturn(0L);

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminApiDatabaseStats_adminUser_returnsStats() throws Exception {
        User admin = User.builder()
                .id(UUID.randomUUID())
                .username("admin")
                .role(User.UserRole.ADMIN)
                .build();
        when(userService.getCurrentUser()).thenReturn(Optional.of(admin));
        when(databaseStatsService.getDatabaseOverview()).thenReturn(Map.of());
        when(databaseStatsService.getTableStats()).thenReturn(List.of());
        when(databaseStatsService.getIndexStats()).thenReturn(List.of());
        when(databaseStatsService.getActiveConnections()).thenReturn(List.of());
        when(databaseStatsService.getSlowQueries()).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/database/stats"))
                .andExpect(status().isOk());
    }
}
