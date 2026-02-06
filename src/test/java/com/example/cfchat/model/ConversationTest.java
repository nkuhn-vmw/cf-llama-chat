package com.example.cfchat.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationTest {

    @Test
    void builder_defaultMessages_emptyList() {
        Conversation conv = Conversation.builder().title("Test").build();
        assertThat(conv.getMessages()).isNotNull().isEmpty();
    }

    @Test
    void addMessage_addsAndSetsBackReference() {
        Conversation conv = Conversation.builder()
                .id(UUID.randomUUID())
                .title("Test")
                .messages(new ArrayList<>())
                .build();

        Message msg = Message.builder()
                .role(Message.MessageRole.USER)
                .content("Hello")
                .build();

        conv.addMessage(msg);

        assertThat(conv.getMessages()).hasSize(1);
        assertThat(conv.getMessages().get(0).getContent()).isEqualTo("Hello");
        assertThat(msg.getConversation()).isEqualTo(conv);
    }

    @Test
    void onCreate_setsTimestamps() {
        Conversation conv = Conversation.builder().build();
        conv.onCreate();
        assertThat(conv.getCreatedAt()).isNotNull();
        assertThat(conv.getUpdatedAt()).isNotNull();
    }

    @Test
    void onCreate_nullTitle_setsDefault() {
        Conversation conv = Conversation.builder().title(null).build();
        conv.onCreate();
        assertThat(conv.getTitle()).isEqualTo("New Conversation");
    }

    @Test
    void onCreate_blankTitle_setsDefault() {
        Conversation conv = Conversation.builder().title("   ").build();
        conv.onCreate();
        assertThat(conv.getTitle()).isEqualTo("New Conversation");
    }

    @Test
    void onCreate_validTitle_keepsTittle() {
        Conversation conv = Conversation.builder().title("My Chat").build();
        conv.onCreate();
        assertThat(conv.getTitle()).isEqualTo("My Chat");
    }

    @Test
    void onUpdate_updatesTimestamp() {
        Conversation conv = Conversation.builder().title("Test").build();
        conv.onUpdate();
        assertThat(conv.getUpdatedAt()).isNotNull();
    }
}
