package com.example.cfchat.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void defaults_useToolsTrue_useDocumentContextFalse() {
        ChatRequest request = ChatRequest.builder()
                .message("Hello")
                .build();

        assertThat(request.isUseTools()).isTrue();
        assertThat(request.isUseDocumentContext()).isFalse();
    }

    @Test
    void validation_blankMessage_hasViolation() {
        ChatRequest request = ChatRequest.builder()
                .message("")
                .build();

        var violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations.iterator().next().getMessage()).isEqualTo("Message content is required");
    }

    @Test
    void validation_nullMessage_hasViolation() {
        ChatRequest request = ChatRequest.builder()
                .message(null)
                .build();

        var violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void validation_validMessage_noViolations() {
        ChatRequest request = ChatRequest.builder()
                .message("Hello world")
                .build();

        var violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void allFields_setCorrectly() {
        UUID convId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();

        ChatRequest request = ChatRequest.builder()
                .conversationId(convId)
                .message("Test message")
                .provider("openai")
                .model("gpt-4o")
                .skillId(skillId)
                .useDocumentContext(true)
                .useTools(false)
                .build();

        assertThat(request.getConversationId()).isEqualTo(convId);
        assertThat(request.getMessage()).isEqualTo("Test message");
        assertThat(request.getProvider()).isEqualTo("openai");
        assertThat(request.getModel()).isEqualTo("gpt-4o");
        assertThat(request.getSkillId()).isEqualTo(skillId);
        assertThat(request.isUseDocumentContext()).isTrue();
        assertThat(request.isUseTools()).isFalse();
    }
}
