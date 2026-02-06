package com.example.cfchat.service;

import com.example.cfchat.model.EmbeddingMetric;
import com.example.cfchat.model.UsageMetric;
import com.example.cfchat.repository.EmbeddingMetricRepository;
import com.example.cfchat.repository.UsageMetricRepository;
import com.example.cfchat.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetricsServiceTest {

    @Mock
    private UsageMetricRepository usageMetricRepository;

    @Mock
    private EmbeddingMetricRepository embeddingMetricRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MetricsService metricsService;

    @Test
    void recordUsage_savesMetric() {
        UUID userId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();

        when(usageMetricRepository.save(any(UsageMetric.class))).thenAnswer(i -> i.getArgument(0));

        UsageMetric result = metricsService.recordUsage(
                userId, convId, "gpt-4o", "openai", 100, 200, 1500L, 300L, 45.5);

        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getConversationId()).isEqualTo(convId);
        assertThat(result.getModel()).isEqualTo("gpt-4o");
        assertThat(result.getPromptTokens()).isEqualTo(100);
        assertThat(result.getCompletionTokens()).isEqualTo(200);
        assertThat(result.getResponseTimeMs()).isEqualTo(1500L);
        assertThat(result.getTimeToFirstTokenMs()).isEqualTo(300L);
        assertThat(result.getTokensPerSecond()).isEqualTo(45.5);
    }

    @Test
    void recordUsage_nullTokens_defaultsToZero() {
        when(usageMetricRepository.save(any(UsageMetric.class))).thenAnswer(i -> i.getArgument(0));

        UsageMetric result = metricsService.recordUsage(
                UUID.randomUUID(), UUID.randomUUID(), "model", "provider",
                null, null, 1000L);

        assertThat(result.getPromptTokens()).isZero();
        assertThat(result.getCompletionTokens()).isZero();
    }

    @Test
    void recordUsage_overloadWithoutPerformanceMetrics_works() {
        when(usageMetricRepository.save(any(UsageMetric.class))).thenAnswer(i -> i.getArgument(0));

        UsageMetric result = metricsService.recordUsage(
                UUID.randomUUID(), UUID.randomUUID(), "model", "provider",
                50, 100, 2000L);

        assertThat(result.getTimeToFirstTokenMs()).isNull();
        assertThat(result.getTokensPerSecond()).isNull();
    }

    @Test
    void recordEmbeddingUsage_savesMetric() {
        when(embeddingMetricRepository.save(any(EmbeddingMetric.class))).thenAnswer(i -> i.getArgument(0));

        EmbeddingMetric result = metricsService.recordEmbeddingUsage(
                UUID.randomUUID(), UUID.randomUUID(), "nomic-embed",
                10, 5000L, 2000L, EmbeddingMetric.OperationType.DOCUMENT_UPLOAD);

        assertThat(result.getModel()).isEqualTo("nomic-embed");
        assertThat(result.getChunkCount()).isEqualTo(10);
        assertThat(result.getTotalCharacters()).isEqualTo(5000L);
    }

    @Test
    void recordEmbeddingUsage_nullDefaults() {
        when(embeddingMetricRepository.save(any(EmbeddingMetric.class))).thenAnswer(i -> i.getArgument(0));

        EmbeddingMetric result = metricsService.recordEmbeddingUsage(
                UUID.randomUUID(), UUID.randomUUID(), "model",
                null, null, 1000L, null);

        assertThat(result.getChunkCount()).isZero();
        assertThat(result.getTotalCharacters()).isZero();
        assertThat(result.getOperationType()).isEqualTo(EmbeddingMetric.OperationType.DOCUMENT_UPLOAD);
    }

    @Test
    void clearAllMetrics_deletesAll() {
        metricsService.clearAllMetrics();
        verify(usageMetricRepository).deleteAll();
    }

    @Test
    void clearAllEmbeddingMetrics_deletesAll() {
        metricsService.clearAllEmbeddingMetrics();
        verify(embeddingMetricRepository).deleteAll();
    }

    @Test
    void getTotalMetricsCount_returnsCount() {
        when(usageMetricRepository.count()).thenReturn(42L);
        assertThat(metricsService.getTotalMetricsCount()).isEqualTo(42L);
    }
}
