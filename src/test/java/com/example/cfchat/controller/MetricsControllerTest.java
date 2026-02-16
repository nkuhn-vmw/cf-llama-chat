package com.example.cfchat.controller;

import com.example.cfchat.auth.UserService;
import com.example.cfchat.model.User;
import com.example.cfchat.service.MetricsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MetricsController.class)
class MetricsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MetricsService metricsService;

    @MockBean
    private UserService userService;

    @MockBean
    private com.example.cfchat.service.RateLimitService rateLimitService;

    @Test
    @WithMockUser(username = "testuser")
    void getSummary_authenticated_returnsSummary() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).username("testuser").role(User.UserRole.USER).build();
        when(userService.getCurrentUser()).thenReturn(Optional.of(user));
        when(metricsService.getSummaryForUser(userId)).thenReturn(Map.of(
                "totalRequests", 10,
                "totalTokens", 5000
        ));

        mockMvc.perform(get("/api/metrics/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(10))
                .andExpect(jsonPath("$.totalTokens").value(5000));
    }

    @Test
    @WithMockUser(username = "testuser")
    void getSummary_noUser_returns401() throws Exception {
        when(userService.getCurrentUser()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/metrics/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getSummary_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/metrics/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getGlobalSummary_admin_returnsGlobalMetrics() throws Exception {
        User admin = User.builder()
                .id(UUID.randomUUID())
                .username("admin")
                .role(User.UserRole.ADMIN)
                .build();
        when(userService.getCurrentUser()).thenReturn(Optional.of(admin));
        when(metricsService.getGlobalSummary()).thenReturn(Map.of(
                "totalRequests", 100
        ));

        mockMvc.perform(get("/api/metrics/global"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(100));
    }

    @Test
    @WithMockUser(username = "testuser")
    void getGlobalSummary_regularUser_returns403() throws Exception {
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .role(User.UserRole.USER)
                .build();
        when(userService.getCurrentUser()).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/metrics/global"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void clearAllMetrics_admin_clearsAndReturnsCount() throws Exception {
        User admin = User.builder()
                .id(UUID.randomUUID())
                .username("admin")
                .role(User.UserRole.ADMIN)
                .build();
        when(userService.getCurrentUser()).thenReturn(Optional.of(admin));
        when(metricsService.getTotalMetricsCount()).thenReturn(42L);
        when(metricsService.getTotalEmbeddingMetricsCount()).thenReturn(8L);

        mockMvc.perform(delete("/api/admin/metrics").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.recordsDeleted").value(50));

        verify(metricsService).clearAllMetrics();
        verify(metricsService).clearAllEmbeddingMetrics();
    }

    @Test
    @WithMockUser(username = "testuser")
    void clearAllMetrics_regularUser_returns403() throws Exception {
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .role(User.UserRole.USER)
                .build();
        when(userService.getCurrentUser()).thenReturn(Optional.of(user));

        mockMvc.perform(delete("/api/admin/metrics").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "testuser")
    void getModelStats_authenticated_returnsStats() throws Exception {
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .role(User.UserRole.USER)
                .build();
        when(userService.getCurrentUser()).thenReturn(Optional.of(user));
        when(metricsService.getModelPerformanceStats()).thenReturn(Map.of("models", List.of()));

        mockMvc.perform(get("/api/metrics/models"))
                .andExpect(status().isOk());
    }
}
