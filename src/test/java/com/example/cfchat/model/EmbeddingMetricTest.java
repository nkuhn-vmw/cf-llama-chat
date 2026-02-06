package com.example.cfchat.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingMetricTest {

    @Test
    void builder_defaults_areSet() {
        EmbeddingMetric metric = EmbeddingMetric.builder()
                .model("nomic-embed")
                .build();

        assertThat(metric.getChunkCount()).isZero();
        assertThat(metric.getTotalCharacters()).isZero();
        assertThat(metric.getOperationType()).isEqualTo(EmbeddingMetric.OperationType.DOCUMENT_UPLOAD);
    }

    @Test
    void operationType_values_containsExpected() {
        assertThat(EmbeddingMetric.OperationType.values()).containsExactly(
                EmbeddingMetric.OperationType.DOCUMENT_UPLOAD,
                EmbeddingMetric.OperationType.SIMILARITY_SEARCH
        );
    }

    @Test
    void onCreate_setsTimestamp() {
        EmbeddingMetric metric = EmbeddingMetric.builder()
                .model("nomic-embed")
                .build();

        metric.onCreate();

        assertThat(metric.getTimestamp()).isNotNull();
    }

    @Test
    void onCreate_preservesExistingTimestamp() {
        LocalDateTime customTime = LocalDateTime.of(2024, 1, 1, 12, 0);

        EmbeddingMetric metric = EmbeddingMetric.builder()
                .model("nomic-embed")
                .timestamp(customTime)
                .build();

        metric.onCreate();

        assertThat(metric.getTimestamp()).isEqualTo(customTime);
    }

    @Test
    void allFields_setCorrectly() {
        UUID userId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        EmbeddingMetric metric = EmbeddingMetric.builder()
                .userId(userId)
                .documentId(docId)
                .model("nomic-embed")
                .chunkCount(10)
                .totalCharacters(5000L)
                .processingTimeMs(2000L)
                .operationType(EmbeddingMetric.OperationType.SIMILARITY_SEARCH)
                .build();

        assertThat(metric.getUserId()).isEqualTo(userId);
        assertThat(metric.getDocumentId()).isEqualTo(docId);
        assertThat(metric.getModel()).isEqualTo("nomic-embed");
        assertThat(metric.getChunkCount()).isEqualTo(10);
        assertThat(metric.getTotalCharacters()).isEqualTo(5000L);
        assertThat(metric.getProcessingTimeMs()).isEqualTo(2000L);
        assertThat(metric.getOperationType()).isEqualTo(EmbeddingMetric.OperationType.SIMILARITY_SEARCH);
    }
}
