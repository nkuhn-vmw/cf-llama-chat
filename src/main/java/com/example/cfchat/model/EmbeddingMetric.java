package com.example.cfchat.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "embedding_metrics", indexes = {
        @Index(name = "idx_embedding_metrics_user_id", columnList = "user_id"),
        @Index(name = "idx_embedding_metrics_timestamp", columnList = "timestamp"),
        @Index(name = "idx_embedding_metrics_model", columnList = "model"),
        @Index(name = "idx_embedding_metrics_user_timestamp", columnList = "user_id, timestamp")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmbeddingMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(nullable = false)
    private String model;

    @Column(name = "chunk_count")
    @Builder.Default
    private Integer chunkCount = 0;

    @Column(name = "total_characters")
    @Builder.Default
    private Long totalCharacters = 0L;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    @Column(name = "operation_type")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private OperationType operationType = OperationType.DOCUMENT_UPLOAD;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }

    public enum OperationType {
        DOCUMENT_UPLOAD,  // Embedding documents for RAG
        SIMILARITY_SEARCH // Embedding query for search
    }
}
