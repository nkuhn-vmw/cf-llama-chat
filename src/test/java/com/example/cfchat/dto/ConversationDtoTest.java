package com.example.cfchat.dto;

import com.example.cfchat.model.Conversation;
import com.example.cfchat.model.Message;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationDtoTest {

    @Test
    void fromEntity_withoutMessages_excludesMessages() {
        Conversation conv = buildConversation("Test", 3);

        ConversationDto dto = ConversationDto.fromEntity(conv, false);

        assertThat(dto.getId()).isEqualTo(conv.getId());
        assertThat(dto.getTitle()).isEqualTo("Test");
        assertThat(dto.getMessageCount()).isEqualTo(3);
        assertThat(dto.getMessages()).isNull();
    }

    @Test
    void fromEntity_withMessages_includesMessages() {
        Conversation conv = buildConversation("Test", 2);

        ConversationDto dto = ConversationDto.fromEntity(conv, true);

        assertThat(dto.getMessages()).hasSize(2);
        assertThat(dto.getMessageCount()).isEqualTo(2);
    }

    @Test
    void fromEntity_copiesAllFields() {
        Conversation conv = buildConversation("My Chat", 0);
        conv.setModelProvider("openai");
        conv.setModelName("gpt-4o");

        ConversationDto dto = ConversationDto.fromEntity(conv, false);

        assertThat(dto.getModelProvider()).isEqualTo("openai");
        assertThat(dto.getModelName()).isEqualTo("gpt-4o");
        assertThat(dto.getCreatedAt()).isEqualTo(conv.getCreatedAt());
        assertThat(dto.getUpdatedAt()).isEqualTo(conv.getUpdatedAt());
    }

    private Conversation buildConversation(String title, int messageCount) {
        Conversation conv = Conversation.builder()
                .id(UUID.randomUUID())
                .title(title)
                .messages(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        for (int i = 0; i < messageCount; i++) {
            Message msg = Message.builder()
                    .id(UUID.randomUUID())
                    .role(i % 2 == 0 ? Message.MessageRole.USER : Message.MessageRole.ASSISTANT)
                    .content("Message " + i)
                    .createdAt(LocalDateTime.now())
                    .build();
            msg.setConversation(conv);
            conv.getMessages().add(msg);
        }

        return conv;
    }
}
