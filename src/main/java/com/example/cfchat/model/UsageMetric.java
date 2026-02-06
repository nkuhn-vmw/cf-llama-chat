package com.example.cfchat.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "usage_metrics", indexes = {
        @Index(name = "idx_usage_metrics_user_id", columnList = "user_id"),
        @Index(name = "idx_usage_metrics_timestamp", columnList = "timestamp"),
        @Index(name = "idx_usage_metrics_model", columnList = "model")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false)
    private String provider;

    @Column(name = "prompt_tokens")
    @Builder.Default
    private Integer promptTokens = 0;

    @Column(name = "completion_tokens")
    @Builder.Default
    private Integer completionTokens = 0;

    @Column(name = "total_tokens")
    @Builder.Default
    private Integer totalTokens = 0;

    @Column(name = "response_time_ms")
    private Long responseTimeMs;

    @Column(name = "time_to_first_token_ms")
    private Long timeToFirstTokenMs;

    @Column(name = "tokens_per_second")
    private Double tokensPerSecond;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        if (totalTokens == null || totalTokens == 0) {
            totalTokens = (promptTokens != null ? promptTokens : 0) + (completionTokens != null ? completionTokens : 0);
        }
    }
}
