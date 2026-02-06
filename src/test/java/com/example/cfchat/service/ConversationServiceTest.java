package com.example.cfchat.service;

import com.example.cfchat.dto.ConversationDto;
import com.example.cfchat.model.Conversation;
import com.example.cfchat.model.Message;
import com.example.cfchat.repository.ConversationRepository;
import com.example.cfchat.repository.MessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MessageRepository messageRepository;

    @InjectMocks
    private ConversationService conversationService;

    @Test
    void createConversation_withTitle_usesProvidedTitle() {
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(i -> {
            Conversation c = i.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        Conversation result = conversationService.createConversation("My Chat", "openai", "gpt-4o", null);

        assertThat(result.getTitle()).isEqualTo("My Chat");
        assertThat(result.getModelProvider()).isEqualTo("openai");
        assertThat(result.getModelName()).isEqualTo("gpt-4o");
    }

    @Test
    void createConversation_nullTitle_usesDefault() {
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(i -> {
            Conversation c = i.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        Conversation result = conversationService.createConversation(null, "openai", "gpt-4o", null);

        assertThat(result.getTitle()).isEqualTo("New Conversation");
    }

    @Test
    void createConversation_withUserId_setsUserId() {
        UUID userId = UUID.randomUUID();
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(i -> {
            Conversation c = i.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        Conversation result = conversationService.createConversation("Title", "openai", "gpt-4o", userId);

        assertThat(result.getUserId()).isEqualTo(userId);
    }

    @Test
    void getConversationsForUser_returnsUserConversations() {
        UUID userId = UUID.randomUUID();
        Conversation conv = buildConversation("Test Conv");
        when(conversationRepository.findByUserIdOrderByUpdatedAtDesc(eq(userId), any())).thenReturn(List.of(conv));

        List<ConversationDto> result = conversationService.getConversationsForUser(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Test Conv");
    }

    @Test
    void getConversation_existing_returnsDto() {
        UUID id = UUID.randomUUID();
        Conversation conv = buildConversation("Found");
        when(conversationRepository.findByIdWithMessages(id)).thenReturn(Optional.of(conv));

        Optional<ConversationDto> result = conversationService.getConversation(id);

        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("Found");
    }

    @Test
    void getConversation_notFound_returnsEmpty() {
        UUID id = UUID.randomUUID();
        when(conversationRepository.findByIdWithMessages(id)).thenReturn(Optional.empty());

        Optional<ConversationDto> result = conversationService.getConversation(id);

        assertThat(result).isEmpty();
    }

    @Test
    void addMessage_validConversation_addsMessage() {
        UUID convId = UUID.randomUUID();
        Conversation conv = buildConversation("Chat");
        conv.setId(convId);
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conv));
        when(conversationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Message result = conversationService.addMessage(convId, Message.MessageRole.USER, "Hello", null);

        assertThat(result.getContent()).isEqualTo("Hello");
        assertThat(result.getRole()).isEqualTo(Message.MessageRole.USER);
    }

    @Test
    void addMessage_nonexistentConversation_throwsException() {
        UUID convId = UUID.randomUUID();
        when(conversationRepository.findById(convId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversationService.addMessage(convId, Message.MessageRole.USER, "Hello", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Conversation not found");
    }

    @Test
    void updateConversationTitle_existing_updatesTitle() {
        UUID id = UUID.randomUUID();
        Conversation conv = buildConversation("Old Title");
        when(conversationRepository.findById(id)).thenReturn(Optional.of(conv));

        conversationService.updateConversationTitle(id, "New Title");

        verify(conversationRepository).save(argThat(c -> c.getTitle().equals("New Title")));
    }

    @Test
    void deleteConversation_callsRepository() {
        UUID id = UUID.randomUUID();
        conversationService.deleteConversation(id);
        verify(conversationRepository).deleteById(id);
    }

    @Test
    void deleteAllConversationsForUser_returnsDeletedCount() {
        UUID userId = UUID.randomUUID();
        when(conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId))
                .thenReturn(List.of(buildConversation("C1"), buildConversation("C2")));

        int deleted = conversationService.deleteAllConversationsForUser(userId);

        assertThat(deleted).isEqualTo(2);
        verify(conversationRepository).deleteByUserId(userId);
    }

    @Test
    void getConversationCountForUser_returnsCount() {
        UUID userId = UUID.randomUUID();
        when(conversationRepository.countByUserId(userId)).thenReturn(42L);

        assertThat(conversationService.getConversationCountForUser(userId)).isEqualTo(42L);
    }

    private Conversation buildConversation(String title) {
        return Conversation.builder()
                .id(UUID.randomUUID())
                .title(title)
                .modelProvider("openai")
                .modelName("gpt-4o")
                .messages(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
