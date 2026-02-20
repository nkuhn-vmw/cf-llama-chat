package com.example.cfchat.service;

import com.example.cfchat.model.Conversation;
import com.example.cfchat.model.Message;
import com.example.cfchat.repository.ConversationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatExportServiceTest {

    @Mock
    private ConversationRepository convRepo;

    @Mock
    private ConversationService convService;

    @Spy
    private ObjectMapper mapper = createObjectMapper();

    @InjectMocks
    private ChatExportService chatExportService;

    private UUID userId;
    private UUID conversationId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
    }

    @Test
    void exportJson_validConversation_returnsJsonBytes() throws Exception {
        Conversation conv = buildConversation(conversationId, "Test Chat", "gpt-4o");
        addMessage(conv, Message.MessageRole.USER, "Hello", null);
        addMessage(conv, Message.MessageRole.ASSISTANT, "Hi there!", "gpt-4o");
        when(convService.getConversationEntityForUser(conversationId, userId)).thenReturn(Optional.of(conv));

        byte[] result = chatExportService.exportJson(conversationId, userId);

        String json = new String(result, StandardCharsets.UTF_8);
        assertThat(json).contains("\"version\" : \"1.0\"");
        assertThat(json).contains("\"source\" : \"cf-llama-chat\"");
        assertThat(json).contains("\"title\" : \"Test Chat\"");
        assertThat(json).contains("\"content\" : \"Hello\"");
        assertThat(json).contains("\"content\" : \"Hi there!\"");
        assertThat(json).contains("\"role\" : \"USER\"");
        assertThat(json).contains("\"role\" : \"ASSISTANT\"");
    }

    @Test
    void exportJson_emptyMessages_returnsJsonWithEmptyMessages() throws Exception {
        Conversation conv = buildConversation(conversationId, "Empty Chat", "gpt-4o");
        when(convService.getConversationEntityForUser(conversationId, userId)).thenReturn(Optional.of(conv));

        byte[] result = chatExportService.exportJson(conversationId, userId);

        String json = new String(result, StandardCharsets.UTF_8);
        assertThat(json).contains("\"title\" : \"Empty Chat\"");
        assertThat(json).contains("\"messages\" : [ ]");
    }

    @Test
    void exportJson_conversationNotFound_throwsException() {
        when(convService.getConversationEntityForUser(conversationId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatExportService.exportJson(conversationId, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Conversation not found or access denied");
    }

    @Test
    void exportJson_filtersInactiveMessages() throws Exception {
        Conversation conv = buildConversation(conversationId, "Chat", "gpt-4o");
        addMessage(conv, Message.MessageRole.USER, "Active message", null);
        Message inactive = addMessage(conv, Message.MessageRole.ASSISTANT, "Inactive message", "gpt-4o");
        inactive.setActive(false);
        when(convService.getConversationEntityForUser(conversationId, userId)).thenReturn(Optional.of(conv));

        byte[] result = chatExportService.exportJson(conversationId, userId);

        String json = new String(result, StandardCharsets.UTF_8);
        assertThat(json).contains("Active message");
        assertThat(json).doesNotContain("Inactive message");
    }

    @Test
    void exportTxt_validConversation_returnsFormattedText() {
        Conversation conv = buildConversation(conversationId, "My Conversation", "gpt-4o");
        addMessage(conv, Message.MessageRole.USER, "What is 2+2?", null);
        addMessage(conv, Message.MessageRole.ASSISTANT, "4", "gpt-4o");
        when(convService.getConversationEntityForUser(conversationId, userId)).thenReturn(Optional.of(conv));

        byte[] result = chatExportService.exportTxt(conversationId, userId);

        String text = new String(result, StandardCharsets.UTF_8);
        assertThat(text).startsWith("# My Conversation");
        assertThat(text).contains("USER:\nWhat is 2+2?");
        assertThat(text).contains("ASSISTANT:\n4");
    }

    @Test
    void exportTxt_emptyMessages_returnsHeaderOnly() {
        Conversation conv = buildConversation(conversationId, "Empty", "gpt-4o");
        conv.setMessages(null);
        when(convService.getConversationEntityForUser(conversationId, userId)).thenReturn(Optional.of(conv));

        byte[] result = chatExportService.exportTxt(conversationId, userId);

        String text = new String(result, StandardCharsets.UTF_8);
        assertThat(text).isEqualTo("# Empty\n\n");
    }

    @Test
    void exportTxt_filtersInactiveMessages() {
        Conversation conv = buildConversation(conversationId, "Chat", "gpt-4o");
        addMessage(conv, Message.MessageRole.USER, "Active", null);
        Message inactive = addMessage(conv, Message.MessageRole.ASSISTANT, "Deleted", "gpt-4o");
        inactive.setActive(false);
        when(convService.getConversationEntityForUser(conversationId, userId)).thenReturn(Optional.of(conv));

        byte[] result = chatExportService.exportTxt(conversationId, userId);

        String text = new String(result, StandardCharsets.UTF_8);
        assertThat(text).contains("Active");
        assertThat(text).doesNotContain("Deleted");
    }

    @Test
    void exportTxt_conversationNotFound_throwsException() {
        when(convService.getConversationEntityForUser(conversationId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatExportService.exportTxt(conversationId, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Conversation not found or access denied");
    }

    @Test
    void exportJson_includesModelName() throws Exception {
        Conversation conv = buildConversation(conversationId, "Chat", "llama-3.1");
        when(convService.getConversationEntityForUser(conversationId, userId)).thenReturn(Optional.of(conv));

        byte[] result = chatExportService.exportJson(conversationId, userId);

        String json = new String(result, StandardCharsets.UTF_8);
        assertThat(json).contains("\"modelName\" : \"llama-3.1\"");
    }

    @Test
    void exportJson_messageIncludesModelUsed() throws Exception {
        Conversation conv = buildConversation(conversationId, "Chat", "gpt-4o");
        addMessage(conv, Message.MessageRole.ASSISTANT, "Response", "gpt-4o");
        when(convService.getConversationEntityForUser(conversationId, userId)).thenReturn(Optional.of(conv));

        byte[] result = chatExportService.exportJson(conversationId, userId);

        String json = new String(result, StandardCharsets.UTF_8);
        assertThat(json).contains("\"modelUsed\" : \"gpt-4o\"");
    }

    private Conversation buildConversation(UUID id, String title, String modelName) {
        return Conversation.builder()
                .id(id)
                .userId(userId)
                .title(title)
                .modelProvider("openai")
                .modelName(modelName)
                .messages(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private Message addMessage(Conversation conv, Message.MessageRole role, String content, String modelUsed) {
        Message msg = Message.builder()
                .id(UUID.randomUUID())
                .role(role)
                .content(content)
                .modelUsed(modelUsed)
                .active(true)
                .createdAt(LocalDateTime.now())
                .conversation(conv)
                .build();
        conv.getMessages().add(msg);
        return msg;
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return om;
    }
}
