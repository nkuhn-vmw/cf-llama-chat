package com.example.cfchat.dto;

import com.example.cfchat.model.Message;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MessageDtoTest {

    @Test
    void fromEntity_mapsAllFields() {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        Message msg = Message.builder()
                .id(id)
                .role(Message.MessageRole.ASSISTANT)
                .content("Hello")
                .modelUsed("gpt-4o")
                .createdAt(now)
                .build();

        MessageDto dto = MessageDto.fromEntity(msg);

        assertThat(dto.getId()).isEqualTo(id);
        assertThat(dto.getRole()).isEqualTo("assistant");
        assertThat(dto.getContent()).isEqualTo("Hello");
        assertThat(dto.getModelUsed()).isEqualTo("gpt-4o");
        assertThat(dto.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void fromEntity_userRole_lowercased() {
        Message msg = Message.builder()
                .id(UUID.randomUUID())
                .role(Message.MessageRole.USER)
                .content("Hi")
                .createdAt(LocalDateTime.now())
                .build();

        MessageDto dto = MessageDto.fromEntity(msg);

        assertThat(dto.getRole()).isEqualTo("user");
    }

    @Test
    void fromEntity_systemRole_lowercased() {
        Message msg = Message.builder()
                .id(UUID.randomUUID())
                .role(Message.MessageRole.SYSTEM)
                .content("System message")
                .createdAt(LocalDateTime.now())
                .build();

        MessageDto dto = MessageDto.fromEntity(msg);

        assertThat(dto.getRole()).isEqualTo("system");
    }

    @Test
    void fromEntity_nullModel_preservesNull() {
        Message msg = Message.builder()
                .id(UUID.randomUUID())
                .role(Message.MessageRole.USER)
                .content("Hi")
                .modelUsed(null)
                .createdAt(LocalDateTime.now())
                .build();

        MessageDto dto = MessageDto.fromEntity(msg);

        assertThat(dto.getModelUsed()).isNull();
    }

    @Test
    void htmlContent_defaultNull_canBeSet() {
        MessageDto dto = MessageDto.builder()
                .content("**bold**")
                .build();

        assertThat(dto.getHtmlContent()).isNull();

        dto.setHtmlContent("<p><strong>bold</strong></p>");
        assertThat(dto.getHtmlContent()).isEqualTo("<p><strong>bold</strong></p>");
    }
}
