package com.example.cfchat.service;

import com.example.cfchat.model.UsageMetric;
import com.example.cfchat.model.User;
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

        // Usage by user (for admins)
        List<Object[]> tokensByUser = usageMetricRepository.sumTokensByUser();
        List<Map<String, Object>> userUsage = new ArrayList<>();
        for (Object[] row : tokensByUser) {
            UUID userId = (UUID) row[0];
            Long tokens = (Long) row[1];

            Optional<User> user = userRepository.findById(userId);
            Map<String, Object> userData = new HashMap<>();
            userData.put("userId", userId);
            userData.put("totalTokens", tokens);
            user.ifPresent(u -> {
                userData.put("username", u.getUsername());
                userData.put("displayName", u.getDisplayName());
            });
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
}
