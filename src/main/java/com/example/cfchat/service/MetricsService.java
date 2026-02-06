package com.example.cfchat.service;

import com.example.cfchat.model.EmbeddingMetric;
import com.example.cfchat.model.EmbeddingMetric.OperationType;
import com.example.cfchat.model.UsageMetric;
import com.example.cfchat.model.User;
import com.example.cfchat.repository.EmbeddingMetricRepository;
import com.example.cfchat.repository.UsageMetricRepository;
import com.example.cfchat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetricsService {

    private final UsageMetricRepository usageMetricRepository;
    private final EmbeddingMetricRepository embeddingMetricRepository;
    private final UserRepository userRepository;

    @Transactional
    public UsageMetric recordUsage(UUID userId, UUID conversationId, String model, String provider,
                                    Integer promptTokens, Integer completionTokens, Long responseTimeMs) {
        return recordUsage(userId, conversationId, model, provider, promptTokens, completionTokens,
                responseTimeMs, null, null);
    }

    @Transactional
    public UsageMetric recordUsage(UUID userId, UUID conversationId, String model, String provider,
                                    Integer promptTokens, Integer completionTokens, Long responseTimeMs,
                                    Long timeToFirstTokenMs, Double tokensPerSecond) {
        UsageMetric metric = UsageMetric.builder()
                .userId(userId)
                .conversationId(conversationId)
                .model(model)
                .provider(provider)
                .promptTokens(promptTokens != null ? promptTokens : 0)
                .completionTokens(completionTokens != null ? completionTokens : 0)
                .responseTimeMs(responseTimeMs)
                .timeToFirstTokenMs(timeToFirstTokenMs)
                .tokensPerSecond(tokensPerSecond)
                .timestamp(LocalDateTime.now())
                .build();

        return usageMetricRepository.save(metric);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSummaryForUser(UUID userId) {
        Map<String, Object> summary = new HashMap<>();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime weekStart = now.minusDays(7);
        LocalDateTime monthStart = now.minusDays(30);

        // Total tokens
        Long totalTokens = usageMetricRepository.sumTotalTokensByUserId(userId);
        Long todayTokens = usageMetricRepository.sumTotalTokensByUserIdAndTimestampAfter(userId, todayStart);
        Long weekTokens = usageMetricRepository.sumTotalTokensByUserIdAndTimestampAfter(userId, weekStart);
        Long monthTokens = usageMetricRepository.sumTotalTokensByUserIdAndTimestampAfter(userId, monthStart);

        summary.put("totalTokens", totalTokens != null ? totalTokens : 0);
        summary.put("todayTokens", todayTokens != null ? todayTokens : 0);
        summary.put("weekTokens", weekTokens != null ? weekTokens : 0);
        summary.put("monthTokens", monthTokens != null ? monthTokens : 0);

        // Request counts
        summary.put("totalRequests", usageMetricRepository.countByUserId(userId));
        summary.put("todayRequests", usageMetricRepository.countByUserIdAndTimestampAfter(userId, todayStart));

        // Tokens by model (all time)
        List<Object[]> tokensByModel = usageMetricRepository.sumTokensByModelForUser(userId);
        Map<String, Long> modelTokens = new LinkedHashMap<>();
        for (Object[] row : tokensByModel) {
            modelTokens.put((String) row[0], (Long) row[1]);
        }
        summary.put("tokensByModel", modelTokens);

        // Tokens by model by time period
        summary.put("tokensByModelToday", getTokensByModelMap(
                usageMetricRepository.sumTokensByModelForUserAfter(userId, todayStart)));
        summary.put("tokensByModelWeek", getTokensByModelMap(
                usageMetricRepository.sumTokensByModelForUserAfter(userId, weekStart)));
        summary.put("tokensByModelMonth", getTokensByModelMap(
                usageMetricRepository.sumTokensByModelForUserAfter(userId, monthStart)));

        // Average response time per request by model
        List<Object[]> avgResponseTimes = usageMetricRepository.avgResponseTimeByModelForUser(userId);
        Map<String, Double> modelResponseTimes = new LinkedHashMap<>();
        for (Object[] row : avgResponseTimes) {
            modelResponseTimes.put((String) row[0], (Double) row[1]);
        }
        summary.put("avgResponseTimeByModel", modelResponseTimes);

        // Average tokens per second by model
        List<Object[]> avgTps = usageMetricRepository.avgTokensPerSecondByModelForUser(userId);
        Map<String, Double> modelTps = new LinkedHashMap<>();
        for (Object[] row : avgTps) {
            modelTps.put((String) row[0], (Double) row[1]);
        }
        summary.put("avgTokensPerSecondByModel", modelTps);

        // Overall average tokens per second
        Double overallTps = usageMetricRepository.avgTokensPerSecondByUserId(userId);
        summary.put("avgTokensPerSecond", overallTps != null ? overallTps : 0.0);

        return summary;
    }

    private Map<String, Long> getTokensByModelMap(List<Object[]> data) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : data) {
            result.put((String) row[0], (Long) row[1]);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getGlobalSummary() {
        Map<String, Object> summary = new HashMap<>();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime weekStart = now.minusDays(7);
        LocalDateTime monthStart = now.minusDays(30);

        // Total tokens
        Long todayTokens = usageMetricRepository.sumTotalTokensByTimestampAfter(todayStart);
        Long weekTokens = usageMetricRepository.sumTotalTokensByTimestampAfter(weekStart);
        Long monthTokens = usageMetricRepository.sumTotalTokensByTimestampAfter(monthStart);

        summary.put("todayTokens", todayTokens != null ? todayTokens : 0);
        summary.put("weekTokens", weekTokens != null ? weekTokens : 0);
        summary.put("monthTokens", monthTokens != null ? monthTokens : 0);

        // Request counts
        summary.put("totalRequests", usageMetricRepository.count());
        summary.put("todayRequests", usageMetricRepository.countByTimestampAfter(todayStart));

        // Tokens by model (all time)
        List<Object[]> tokensByModel = usageMetricRepository.sumTokensByModel();
        Map<String, Long> modelTokens = new LinkedHashMap<>();
        for (Object[] row : tokensByModel) {
            modelTokens.put((String) row[0], (Long) row[1]);
        }
        summary.put("tokensByModel", modelTokens);

        // Tokens by model by time period
        summary.put("tokensByModelToday", getTokensByModelMap(
                usageMetricRepository.sumTokensByModelAfter(todayStart)));
        summary.put("tokensByModelWeek", getTokensByModelMap(
                usageMetricRepository.sumTokensByModelAfter(weekStart)));
        summary.put("tokensByModelMonth", getTokensByModelMap(
                usageMetricRepository.sumTokensByModelAfter(monthStart)));

        // Average response time per request by model (global)
        List<Object[]> avgResponseTimes = usageMetricRepository.avgResponseTimeByModel();
        Map<String, Double> modelResponseTimes = new LinkedHashMap<>();
        for (Object[] row : avgResponseTimes) {
            modelResponseTimes.put((String) row[0], (Double) row[1]);
        }
        summary.put("avgResponseTimeByModel", modelResponseTimes);

        // Average tokens per second by model (global)
        List<Object[]> avgTps = usageMetricRepository.avgTokensPerSecondByModel();
        Map<String, Double> modelTps = new LinkedHashMap<>();
        for (Object[] row : avgTps) {
            modelTps.put((String) row[0], (Double) row[1]);
        }
        summary.put("avgTokensPerSecondByModel", modelTps);

        // Overall average tokens per second
        Double overallTps = usageMetricRepository.avgTokensPerSecond();
        summary.put("avgTokensPerSecond", overallTps != null ? overallTps : 0.0);

        // Average time to first token by model
        List<Object[]> avgTtft = usageMetricRepository.avgTimeToFirstTokenByModel();
        Map<String, Double> modelTtft = new LinkedHashMap<>();
        for (Object[] row : avgTtft) {
            modelTtft.put((String) row[0], (Double) row[1]);
        }
        summary.put("avgTimeToFirstTokenByModel", modelTtft);

        // Overall average time to first token
        Double overallTtft = usageMetricRepository.avgTimeToFirstToken();
        summary.put("avgTimeToFirstToken", overallTtft != null ? overallTtft : 0.0);

        // Request count by model
        List<Object[]> requestsByModel = usageMetricRepository.countRequestsByModel();
        Map<String, Long> modelRequests = new LinkedHashMap<>();
        for (Object[] row : requestsByModel) {
            modelRequests.put((String) row[0], (Long) row[1]);
        }
        summary.put("requestsByModel", modelRequests);

        // Usage by user (for admins) - batch-fetch users to avoid N+1
        List<Object[]> tokensByUser = usageMetricRepository.sumTokensByUser();
        List<UUID> userIds = tokensByUser.stream().map(row -> (UUID) row[0]).toList();
        Map<UUID, User> usersById = userRepository.findAllById(userIds).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, u -> u));
        List<Map<String, Object>> userUsage = new ArrayList<>();
        for (Object[] row : tokensByUser) {
            UUID userId = (UUID) row[0];
            Long tokens = (Long) row[1];

            Map<String, Object> userData = new HashMap<>();
            userData.put("userId", userId);
            userData.put("totalTokens", tokens);
            User user = usersById.get(userId);
            if (user != null) {
                userData.put("username", user.getUsername());
                userData.put("displayName", user.getDisplayName());
            }
            userUsage.add(userData);
        }
        summary.put("usageByUser", userUsage);

        return summary;
    }

    @Transactional(readOnly = true)
    public List<UsageMetric> getRecentUsageForUser(UUID userId, int limit) {
        return usageMetricRepository.findByUserIdOrderByTimestampDesc(userId)
                .stream()
                .limit(limit)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getModelPerformanceStats() {
        Map<String, Object> stats = new HashMap<>();

        List<Object[]> avgResponseTimes = usageMetricRepository.avgResponseTimeByModel();
        List<Object[]> tokensByModel = usageMetricRepository.sumTokensByModel();
        List<Object[]> avgTps = usageMetricRepository.avgTokensPerSecondByModel();
        List<Object[]> avgTtft = usageMetricRepository.avgTimeToFirstTokenByModel();
        List<Object[]> requestCounts = usageMetricRepository.countRequestsByModel();

        Map<String, Long> tokenMap = new HashMap<>();
        Map<String, Double> tpsMap = new HashMap<>();
        Map<String, Double> ttftMap = new HashMap<>();
        Map<String, Long> requestMap = new HashMap<>();

        for (Object[] row : tokensByModel) {
            tokenMap.put((String) row[0], (Long) row[1]);
        }
        for (Object[] row : avgTps) {
            tpsMap.put((String) row[0], (Double) row[1]);
        }
        for (Object[] row : avgTtft) {
            ttftMap.put((String) row[0], (Double) row[1]);
        }
        for (Object[] row : requestCounts) {
            requestMap.put((String) row[0], (Long) row[1]);
        }

        List<Map<String, Object>> modelStats = new ArrayList<>();
        for (Object[] row : avgResponseTimes) {
            String model = (String) row[0];
            Double avgTime = (Double) row[1];

            Map<String, Object> modelStat = new HashMap<>();
            modelStat.put("model", model);
            modelStat.put("avgResponseTimeMs", avgTime);
            modelStat.put("totalTokens", tokenMap.getOrDefault(model, 0L));
            modelStat.put("avgTokensPerSecond", tpsMap.getOrDefault(model, 0.0));
            modelStat.put("avgTimeToFirstTokenMs", ttftMap.getOrDefault(model, 0.0));
            modelStat.put("requestCount", requestMap.getOrDefault(model, 0L));
            modelStats.add(modelStat);
        }

        stats.put("models", modelStats);
        return stats;
    }

    @Transactional
    public void clearAllMetrics() {
        log.info("Clearing all usage metrics");
        usageMetricRepository.deleteAll();
        log.info("All usage metrics cleared");
    }

    @Transactional(readOnly = true)
    public long getTotalMetricsCount() {
        return usageMetricRepository.count();
    }

    // ==================== Embedding Metrics ====================

    @Transactional
    public EmbeddingMetric recordEmbeddingUsage(UUID userId, UUID documentId, String model,
                                                 Integer chunkCount, Long totalCharacters,
                                                 Long processingTimeMs, OperationType operationType) {
        EmbeddingMetric metric = EmbeddingMetric.builder()
                .userId(userId)
                .documentId(documentId)
                .model(model)
                .chunkCount(chunkCount != null ? chunkCount : 0)
                .totalCharacters(totalCharacters != null ? totalCharacters : 0L)
                .processingTimeMs(processingTimeMs)
                .operationType(operationType != null ? operationType : OperationType.DOCUMENT_UPLOAD)
                .timestamp(LocalDateTime.now())
                .build();

        return embeddingMetricRepository.save(metric);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getEmbeddingSummaryForUser(UUID userId) {
        Map<String, Object> summary = new HashMap<>();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime weekStart = now.minusDays(7);
        LocalDateTime monthStart = now.minusDays(30);

        // Total chunks embedded
        Long totalChunks = embeddingMetricRepository.sumChunksByUserId(userId);
        Long todayChunks = embeddingMetricRepository.sumChunksByUserIdAndTimestampAfter(userId, todayStart);

        summary.put("totalChunks", totalChunks != null ? totalChunks : 0);
        summary.put("todayChunks", todayChunks != null ? todayChunks : 0);

        // Total characters
        Long totalCharacters = embeddingMetricRepository.sumCharactersByUserId(userId);
        summary.put("totalCharacters", totalCharacters != null ? totalCharacters : 0);

        // Request counts
        summary.put("totalEmbeddingRequests", embeddingMetricRepository.countByUserId(userId));
        summary.put("todayEmbeddingRequests", embeddingMetricRepository.countByUserIdAndTimestampAfter(userId, todayStart));

        // Average processing time
        Double avgProcessingTime = embeddingMetricRepository.avgProcessingTimeByUserId(userId);
        summary.put("avgProcessingTimeMs", avgProcessingTime != null ? avgProcessingTime : 0.0);

        // Chunks by model
        List<Object[]> chunksByModel = embeddingMetricRepository.sumChunksByModelForUser(userId);
        Map<String, Long> modelChunks = new LinkedHashMap<>();
        for (Object[] row : chunksByModel) {
            modelChunks.put((String) row[0], (Long) row[1]);
        }
        summary.put("chunksByModel", modelChunks);

        return summary;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getGlobalEmbeddingSummary() {
        Map<String, Object> summary = new HashMap<>();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime weekStart = now.minusDays(7);
        LocalDateTime monthStart = now.minusDays(30);

        // Total chunks
        Long totalChunks = embeddingMetricRepository.sumTotalChunks();
        Long todayChunks = embeddingMetricRepository.sumChunksByTimestampAfter(todayStart);
        Long weekChunks = embeddingMetricRepository.sumChunksByTimestampAfter(weekStart);
        Long monthChunks = embeddingMetricRepository.sumChunksByTimestampAfter(monthStart);

        summary.put("totalChunks", totalChunks != null ? totalChunks : 0);
        summary.put("todayChunks", todayChunks != null ? todayChunks : 0);
        summary.put("weekChunks", weekChunks != null ? weekChunks : 0);
        summary.put("monthChunks", monthChunks != null ? monthChunks : 0);

        // Total characters
        Long totalCharacters = embeddingMetricRepository.sumTotalCharacters();
        Long todayCharacters = embeddingMetricRepository.sumCharactersByTimestampAfter(todayStart);
        summary.put("totalCharacters", totalCharacters != null ? totalCharacters : 0);
        summary.put("todayCharacters", todayCharacters != null ? todayCharacters : 0);

        // Request counts
        summary.put("totalEmbeddingRequests", embeddingMetricRepository.count());
        summary.put("todayEmbeddingRequests", embeddingMetricRepository.countByTimestampAfter(todayStart));

        // Average processing time
        Double avgProcessingTime = embeddingMetricRepository.avgProcessingTime();
        summary.put("avgProcessingTimeMs", avgProcessingTime != null ? avgProcessingTime : 0.0);

        // Chunks by model (all time)
        List<Object[]> chunksByModel = embeddingMetricRepository.sumChunksByModel();
        Map<String, Long> modelChunks = new LinkedHashMap<>();
        for (Object[] row : chunksByModel) {
            modelChunks.put((String) row[0], (Long) row[1]);
        }
        summary.put("chunksByModel", modelChunks);

        // Chunks by model by time period
        summary.put("chunksByModelToday", getChunksByModelMap(
                embeddingMetricRepository.sumChunksByModelAfter(todayStart)));
        summary.put("chunksByModelWeek", getChunksByModelMap(
                embeddingMetricRepository.sumChunksByModelAfter(weekStart)));
        summary.put("chunksByModelMonth", getChunksByModelMap(
                embeddingMetricRepository.sumChunksByModelAfter(monthStart)));

        // Request count by model
        List<Object[]> requestsByModel = embeddingMetricRepository.countRequestsByModel();
        Map<String, Long> modelRequests = new LinkedHashMap<>();
        for (Object[] row : requestsByModel) {
            modelRequests.put((String) row[0], (Long) row[1]);
        }
        summary.put("requestsByModel", modelRequests);

        // Average processing time by model
        List<Object[]> avgTimeByModel = embeddingMetricRepository.avgProcessingTimeByModelAll();
        Map<String, Double> modelAvgTime = new LinkedHashMap<>();
        for (Object[] row : avgTimeByModel) {
            modelAvgTime.put((String) row[0], (Double) row[1]);
        }
        summary.put("avgProcessingTimeByModel", modelAvgTime);

        // Usage by user - batch-fetch users to avoid N+1
        List<Object[]> chunksByUser = embeddingMetricRepository.sumChunksByUser();
        List<UUID> embUserIds = chunksByUser.stream().map(row -> (UUID) row[0]).toList();
        Map<UUID, User> embUsersById = userRepository.findAllById(embUserIds).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, u -> u));
        List<Map<String, Object>> userUsage = new ArrayList<>();
        for (Object[] row : chunksByUser) {
            UUID userId = (UUID) row[0];
            Long chunks = (Long) row[1];

            Map<String, Object> userData = new HashMap<>();
            userData.put("userId", userId);
            userData.put("totalChunks", chunks);
            User embUser = embUsersById.get(userId);
            if (embUser != null) {
                userData.put("username", embUser.getUsername());
                userData.put("displayName", embUser.getDisplayName());
            }
            userUsage.add(userData);
        }
        summary.put("usageByUser", userUsage);

        return summary;
    }

    private Map<String, Long> getChunksByModelMap(List<Object[]> data) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : data) {
            result.put((String) row[0], (Long) row[1]);
        }
        return result;
    }

    @Transactional
    public void clearAllEmbeddingMetrics() {
        log.info("Clearing all embedding metrics");
        embeddingMetricRepository.deleteAll();
        log.info("All embedding metrics cleared");
    }

    @Transactional(readOnly = true)
    public long getTotalEmbeddingMetricsCount() {
        return embeddingMetricRepository.count();
    }
}
