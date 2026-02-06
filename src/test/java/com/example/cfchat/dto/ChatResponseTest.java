package com.example.cfchat.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChatResponseTest {

    @Test
    void builder_allFields_setCorrectly() {
        UUID convId = UUID.randomUUID();
        UUID msgId = UUID.randomUUID();

        ChatResponse response = ChatResponse.builder()
                .conversationId(convId)
                .messageId(msgId)
                .content("Hello world")
                .htmlContent("<p>Hello world</p>")
                .model("gpt-4o")
                .error(null)
                .streaming(false)
                .complete(true)
                .timeToFirstTokenMs(250L)
                .tokensPerSecond(45.5)
                .totalResponseTimeMs(1500L)
                .build();

        assertThat(response.getConversationId()).isEqualTo(convId);
        assertThat(response.getMessageId()).isEqualTo(msgId);
        assertThat(response.getContent()).isEqualTo("Hello world");
        assertThat(response.getHtmlContent()).isEqualTo("<p>Hello world</p>");
        assertThat(response.getModel()).isEqualTo("gpt-4o");
        assertThat(response.getError()).isNull();
        assertThat(response.isStreaming()).isFalse();
        assertThat(response.isComplete()).isTrue();
        assertThat(response.getTimeToFirstTokenMs()).isEqualTo(250L);
        assertThat(response.getTokensPerSecond()).isEqualTo(45.5);
        assertThat(response.getTotalResponseTimeMs()).isEqualTo(1500L);
    }

    @Test
    void builder_streamingResponse_fieldsCorrect() {
        ChatResponse response = ChatResponse.builder()
                .content("partial")
                .streaming(true)
                .complete(false)
                .build();

        assertThat(response.isStreaming()).isTrue();
        assertThat(response.isComplete()).isFalse();
    }

    @Test
    void builder_errorResponse_hasErrorField() {
        ChatResponse response = ChatResponse.builder()
                .error("Model unavailable")
                .build();

        assertThat(response.getError()).isEqualTo("Model unavailable");
        assertThat(response.getContent()).isNull();
    }

    @Test
    void noArgsConstructor_defaults() {
        ChatResponse response = new ChatResponse();

        assertThat(response.isStreaming()).isFalse();
        assertThat(response.isComplete()).isFalse();
        assertThat(response.getContent()).isNull();
    }
}
