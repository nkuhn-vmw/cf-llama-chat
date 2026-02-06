package com.example.cfchat.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UsageMetricTest {

    @Test
    void builder_defaults_areSet() {
        UsageMetric metric = UsageMetric.builder()
                .model("gpt-4o")
                .provider("openai")
                .build();

        assertThat(metric.getPromptTokens()).isZero();
        assertThat(metric.getCompletionTokens()).isZero();
        assertThat(metric.getTotalTokens()).isZero();
    }

    @Test
    void onCreate_setsTimestamp() {
        UsageMetric metric = UsageMetric.builder()
                .model("gpt-4o")
                .provider("openai")
                .build();

        metric.onCreate();

        assertThat(metric.getTimestamp()).isNotNull();
    }

    @Test
    void onCreate_calculatesTotalTokens() {
        UsageMetric metric = UsageMetric.builder()
                .model("gpt-4o")
                .provider("openai")
                .promptTokens(100)
                .completionTokens(200)
                .totalTokens(0)
                .build();

        metric.onCreate();

        assertThat(metric.getTotalTokens()).isEqualTo(300);
    }

    @Test
    void onCreate_preservesExistingTimestamp() {
        LocalDateTime customTime = LocalDateTime.of(2024, 1, 1, 12, 0);

        UsageMetric metric = UsageMetric.builder()
                .model("gpt-4o")
                .provider("openai")
                .timestamp(customTime)
                .build();

        metric.onCreate();

        assertThat(metric.getTimestamp()).isEqualTo(customTime);
    }

    @Test
    void onCreate_preservesExistingTotalTokens() {
        UsageMetric metric = UsageMetric.builder()
                .model("gpt-4o")
                .provider("openai")
                .promptTokens(100)
                .completionTokens(200)
                .totalTokens(350) // Manually set (includes overhead)
                .build();

        metric.onCreate();

        assertThat(metric.getTotalTokens()).isEqualTo(350);
    }

    @Test
    void allFields_setCorrectly() {
        UUID userId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();

        UsageMetric metric = UsageMetric.builder()
                .userId(userId)
                .conversationId(convId)
                .model("gpt-4o")
                .provider("openai")
                .promptTokens(100)
                .completionTokens(200)
                .responseTimeMs(1500L)
                .timeToFirstTokenMs(300L)
                .tokensPerSecond(45.5)
                .build();

        assertThat(metric.getUserId()).isEqualTo(userId);
        assertThat(metric.getConversationId()).isEqualTo(convId);
        assertThat(metric.getResponseTimeMs()).isEqualTo(1500L);
        assertThat(metric.getTimeToFirstTokenMs()).isEqualTo(300L);
        assertThat(metric.getTokensPerSecond()).isEqualTo(45.5);
    }
}
