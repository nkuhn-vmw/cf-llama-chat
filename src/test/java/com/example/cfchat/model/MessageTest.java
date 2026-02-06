package com.example.cfchat.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MessageTest {

    @Test
    void messageRole_values_containsExpected() {
        assertThat(Message.MessageRole.values()).containsExactly(
                Message.MessageRole.USER,
                Message.MessageRole.ASSISTANT,
                Message.MessageRole.SYSTEM
        );
    }

    @Test
    void builder_allFields_setCorrectly() {
        UUID id = UUID.randomUUID();

        Message msg = Message.builder()
                .id(id)
                .role(Message.MessageRole.ASSISTANT)
                .content("Hello there")
                .modelUsed("gpt-4o")
                .tokensUsed(150)
                .build();

        assertThat(msg.getId()).isEqualTo(id);
        assertThat(msg.getRole()).isEqualTo(Message.MessageRole.ASSISTANT);
        assertThat(msg.getContent()).isEqualTo("Hello there");
        assertThat(msg.getModelUsed()).isEqualTo("gpt-4o");
        assertThat(msg.getTokensUsed()).isEqualTo(150);
    }

    @Test
    void onCreate_setsTimestamp() {
        Message msg = Message.builder()
                .role(Message.MessageRole.USER)
                .content("Hello")
                .build();

        msg.onCreate();

        assertThat(msg.getCreatedAt()).isNotNull();
    }

    @Test
    void conversationRelationship_canBeSet() {
        Conversation conv = Conversation.builder().id(UUID.randomUUID()).title("Test").build();

        Message msg = Message.builder()
                .role(Message.MessageRole.USER)
                .content("Hello")
                .build();

        msg.setConversation(conv);

        assertThat(msg.getConversation()).isEqualTo(conv);
    }
}
