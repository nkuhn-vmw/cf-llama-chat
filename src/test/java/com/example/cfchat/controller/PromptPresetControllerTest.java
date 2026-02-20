package com.example.cfchat.controller;

import com.example.cfchat.auth.UserService;
import com.example.cfchat.model.PromptPreset;
import com.example.cfchat.model.User;
import com.example.cfchat.service.PromptPresetService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PromptPresetController.class)
@AutoConfigureMockMvc(addFilters = false)
class PromptPresetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PromptPresetService presetService;

    @MockBean
    private UserService userService;

    @MockBean
    private com.example.cfchat.service.RateLimitService rateLimitService;

    private final UUID userId = UUID.randomUUID();

    private void mockCurrentUser() {
        User user = User.builder().id(userId).username("testuser").build();
        when(userService.getCurrentUser()).thenReturn(Optional.of(user));
    }

    private PromptPreset buildPreset(UUID id, String command, String title, String content) {
        return PromptPreset.builder()
                .id(id)
                .command(command)
                .title(title)
                .content(content)
                .description("A test preset")
                .ownerId(userId)
                .shared(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @WithMockUser(username = "testuser")
    void list_returnsPresets() throws Exception {
        mockCurrentUser();
        UUID presetId = UUID.randomUUID();
        PromptPreset preset = buildPreset(presetId, "/greet", "Greeting", "Hello {{name}}");

        when(presetService.getAccessiblePresets(userId)).thenReturn(List.of(preset));

        mockMvc.perform(get("/api/prompts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].command").value("/greet"))
                .andExpect(jsonPath("$[0].title").value("Greeting"))
                .andExpect(jsonPath("$[0].content").value("Hello {{name}}"))
                .andExpect(jsonPath("$[0].description").value("A test preset"))
                .andExpect(jsonPath("$[0].shared").value(false));
    }

    @Test
    @WithMockUser(username = "testuser")
    void create_validRequest_returnsCreatedPreset() throws Exception {
        mockCurrentUser();
        UUID presetId = UUID.randomUUID();
        PromptPreset preset = buildPreset(presetId, "/summarize", "Summarize", "Summarize the following:");

        when(presetService.create(eq(userId), eq("summarize"), eq("Summarize"),
                eq("Summarize the following:"), eq("Summarizes text"), eq(false)))
                .thenReturn(preset);

        mockMvc.perform(post("/api/prompts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "command", "summarize",
                                "title", "Summarize",
                                "content", "Summarize the following:",
                                "description", "Summarizes text",
                                "shared", false
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.command").value("/summarize"))
                .andExpect(jsonPath("$.title").value("Summarize"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void search_returnsMatchingPresets() throws Exception {
        mockCurrentUser();
        UUID presetId = UUID.randomUUID();
        PromptPreset preset = buildPreset(presetId, "/test", "Test Preset", "Test content");

        when(presetService.searchPresets(userId, "test")).thenReturn(List.of(preset));

        mockMvc.perform(get("/api/prompts/search").param("q", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].command").value("/test"))
                .andExpect(jsonPath("$[0].title").value("Test Preset"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void update_validRequest_returnsUpdatedPreset() throws Exception {
        mockCurrentUser();
        UUID presetId = UUID.randomUUID();
        PromptPreset updated = buildPreset(presetId, "/updated", "Updated Title", "Updated content");

        when(presetService.update(eq(presetId), eq(userId), eq("updated"), eq("Updated Title"),
                eq("Updated content"), eq("Updated desc"), eq(true)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/prompts/" + presetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "command", "updated",
                                "title", "Updated Title",
                                "content", "Updated content",
                                "description", "Updated desc",
                                "shared", true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.command").value("/updated"))
                .andExpect(jsonPath("$.title").value("Updated Title"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void delete_validRequest_returns204() throws Exception {
        mockCurrentUser();
        UUID presetId = UUID.randomUUID();

        doNothing().when(presetService).delete(presetId, userId);

        mockMvc.perform(delete("/api/prompts/" + presetId))
                .andExpect(status().isNoContent());

        verify(presetService).delete(presetId, userId);
    }
}
