package com.example.cfchat.controller;

import com.example.cfchat.auth.UserService;
import com.example.cfchat.dto.ConversationDto;
import com.example.cfchat.dto.MessageDto;
import com.example.cfchat.model.Conversation;
import com.example.cfchat.model.Message;
import com.example.cfchat.model.User;
import com.example.cfchat.service.ConversationService;
import com.example.cfchat.service.MarkdownService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ConversationController.class)
class ConversationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ConversationService conversationService;

    @MockBean
    private MarkdownService markdownService;

    @MockBean
    private UserService userService;

    @MockBean
    private com.example.cfchat.service.RateLimitService rateLimitService;

    @MockBean
    private com.example.cfchat.service.SystemSettingService systemSettingService;

    @MockBean
    private com.example.cfchat.repository.ConversationRepository conversationRepository;

    @MockBean
    private com.example.cfchat.repository.MessageRepository messageRepository;

    @Test
    @WithMockUser(username = "testuser")
    void getAllConversations_authenticated_returnsUserConversations() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).username("testuser").build();
        when(userService.getCurrentUser()).thenReturn(Optional.of(user));

        ConversationDto conv = ConversationDto.builder()
                .id(UUID.randomUUID())
                .title("Test Chat")
                .messageCount(3)
                .createdAt(LocalDateTime.now())
                .build();
        when(conversationService.getConversationsForUser(userId)).thenReturn(List.of(conv));

        mockMvc.perform(get("/api/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Test Chat"))
                .andExpect(jsonPath("$[0].messageCount").value(3));
    }

    @Test
    @WithMockUser(username = "testuser")
    void getConversation_found_returnsWithHtmlContent() throws Exception {
        UUID convId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).username("testuser").build();
        when(userService.getCurrentUser()).thenReturn(Optional.of(user));

        MessageDto msg = MessageDto.builder()
                .id(UUID.randomUUID())
                .role("assistant")
                .content("**bold**")
                .build();
        ConversationDto conv = ConversationDto.builder()
                .id(convId)
                .title("Chat")
                .messages(List.of(msg))
                .build();
        when(conversationService.getConversationForUser(convId, userId)).thenReturn(Optional.of(conv));
        when(markdownService.toHtml("**bold**")).thenReturn("<p><strong>bold</strong></p>");

        mockMvc.perform(get("/api/conversations/" + convId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Chat"))
                .andExpect(jsonPath("$.messages[0].htmlContent").value("<p><strong>bold</strong></p>"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void getConversation_notFound_returns404() throws Exception {
        UUID convId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).username("testuser").build();
        when(userService.getCurrentUser()).thenReturn(Optional.of(user));
        when(conversationService.getConversationForUser(convId, userId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/conversations/" + convId))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "testuser")
    void createConversation_withBody_createsConversation() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).username("testuser").build();
        when(userService.getCurrentUser()).thenReturn(Optional.of(user));

        Conversation conv = Conversation.builder()
                .id(UUID.randomUUID())
                .title("New Chat")
                .modelProvider("openai")
                .messages(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        when(conversationService.createConversation("New Chat", "openai", "gpt-4o", userId)).thenReturn(conv);

        mockMvc.perform(post("/api/conversations")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "New Chat",
                                "provider", "openai",
                                "model", "gpt-4o"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New Chat"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void deleteConversation_callsService() throws Exception {
        UUID convId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).username("testuser").build();
        when(userService.getCurrentUser()).thenReturn(Optional.of(user));
        when(conversationService.isOwnedByUser(convId, userId)).thenReturn(true);

        mockMvc.perform(delete("/api/conversations/" + convId).with(csrf()))
                .andExpect(status().isNoContent());

        verify(conversationService).deleteConversation(convId);
    }

    @Test
    @WithMockUser(username = "testuser")
    void updateConversation_updatesTitle() throws Exception {
        UUID convId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).username("testuser").build();
        when(userService.getCurrentUser()).thenReturn(Optional.of(user));
        when(conversationService.isOwnedByUser(convId, userId)).thenReturn(true);

        mockMvc.perform(patch("/api/conversations/" + convId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "Updated Title"))))
                .andExpect(status().isOk());

        verify(conversationService).updateConversationTitle(convId, "Updated Title");
    }

    @Test
    @WithMockUser(username = "testuser")
    void clearAllConversations_authenticated_clearsAndReturnsCount() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).username("testuser").build();
        when(userService.getCurrentUser()).thenReturn(Optional.of(user));
        when(conversationService.deleteAllConversationsForUser(userId)).thenReturn(5);

        mockMvc.perform(delete("/api/conversations/clear-all").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.deletedCount").value(5));
    }

    @Test
    @WithMockUser(username = "testuser")
    void clearAllConversations_noUser_returnsServiceUnavailable() throws Exception {
        when(userService.getCurrentUser()).thenReturn(Optional.empty());

        // requireUserId() throws IllegalStateException → 503
        mockMvc.perform(delete("/api/conversations/clear-all").with(csrf()))
                .andExpect(status().isServiceUnavailable());
    }
}
