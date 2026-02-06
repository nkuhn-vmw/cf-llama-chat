package com.example.cfchat.controller;

import com.example.cfchat.auth.UserService;
import com.example.cfchat.dto.ChatRequest;
import com.example.cfchat.dto.ChatResponse;
import com.example.cfchat.model.ModelInfo;
import com.example.cfchat.model.Skill;
import com.example.cfchat.model.Tool;
import com.example.cfchat.model.ToolType;
import com.example.cfchat.model.User;
import com.example.cfchat.service.ChatService;
import com.example.cfchat.service.McpService;
import com.example.cfchat.service.SkillService;
import com.example.cfchat.service.ToolService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ChatService chatService;

    @MockBean
    private UserService userService;

    @MockBean
    private ToolService toolService;

    @MockBean
    private SkillService skillService;

    @MockBean
    private McpService mcpService;

    @Test
    @WithMockUser(username = "testuser")
    void chat_validRequest_returnsResponse() throws Exception {
        ChatResponse response = ChatResponse.builder()
                .conversationId(UUID.randomUUID())
                .content("Hello!")
                .model("gpt-4o")
                .build();
        when(chatService.chat(any(ChatRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "message", "Hi there"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Hello!"))
                .andExpect(jsonPath("$.model").value("gpt-4o"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void chat_blankMessage_returnsError() throws Exception {
        // Blank message triggers @NotBlank validation -> MethodArgumentNotValidException -> 400
        mockMvc.perform(post("/api/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "message", ""
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chat_unauthenticated_isUnauthorized() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "message", "Hello"
                        ))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "testuser")
    void getModels_returnsModelList() throws Exception {
        List<ModelInfo> models = List.of(
                ModelInfo.builder().name("gpt-4o").provider("openai").available(true).build(),
                ModelInfo.builder().name("llama3.2").provider("ollama").available(true).build()
        );
        when(chatService.getAvailableModels()).thenReturn(models);

        mockMvc.perform(get("/api/chat/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("gpt-4o"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void getAvailableTools_withUser_returnsTools() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).username("testuser").build();
        when(userService.getCurrentUser()).thenReturn(Optional.of(user));

        Tool tool = Tool.builder()
                .id(UUID.randomUUID())
                .name("web_search")
                .displayName("Web Search")
                .description("Search the web")
                .type(ToolType.MCP)
                .build();
        when(toolService.getAccessibleTools(userId)).thenReturn(List.of(tool));

        mockMvc.perform(get("/api/chat/available-tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("web_search"))
                .andExpect(jsonPath("$[0].displayName").value("Web Search"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void getAvailableTools_noUser_returnsEmptyList() throws Exception {
        when(userService.getCurrentUser()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/chat/available-tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser(username = "testuser")
    void getAvailableSkills_withUser_returnsSkills() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).username("testuser").build();
        when(userService.getCurrentUser()).thenReturn(Optional.of(user));

        Skill skill = Skill.builder()
                .id(UUID.randomUUID())
                .name("code_review")
                .displayName("Code Review")
                .description("Review code for issues")
                .build();
        when(skillService.getAccessibleSkills(userId)).thenReturn(List.of(skill));
        when(skillService.getSkillTools(skill)).thenReturn(List.of());

        mockMvc.perform(get("/api/chat/available-skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("code_review"))
                .andExpect(jsonPath("$[0].toolCount").value(0));
    }

    @Test
    @WithMockUser(username = "testuser")
    void getAvailableSkills_noUser_returnsEmptyList() throws Exception {
        when(userService.getCurrentUser()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/chat/available-skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getModels_unauthenticated_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/chat/models"))
                .andExpect(status().isUnauthorized());
    }
}
