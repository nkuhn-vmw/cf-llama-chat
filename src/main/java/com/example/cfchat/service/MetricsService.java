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
        UsageMetric metric = UsageMetric.builder()
                .userId(userId)
                .conversationId(conversationId)
                .model(model)
                .provider(provider)
                .promptTokens(promptTokens != null ? promptTokens : 0)
                .completionTokens(completionTokens != null ? completionTokens : 0)
                .responseTimeMs(responseTimeMs)
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

        // Tokens by model
        List<Object[]> tokensByModel = usageMetricRepository.sumTokensByModelForUser(userId);
        Map<String, Long> modelTokens = new LinkedHashMap<>();
        for (Object[] row : tokensByModel) {
            modelTokens.put((String) row[0], (Long) row[1]);
        }
        summary.put("tokensByModel", modelTokens);

        // Average response time by model
        List<Object[]> avgResponseTimes = usageMetricRepository.avgResponseTimeByModelForUser(userId);
        Map<String, Double> modelResponseTimes = new LinkedHashMap<>();
        for (Object[] row : avgResponseTimes) {
            modelResponseTimes.put((String) row[0], (Double) row[1]);
        }
        summary.put("avgResponseTimeByModel", modelResponseTimes);

        return summary;
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

        // Tokens by model (global)
        List<Object[]> tokensByModel = usageMetricRepository.sumTokensByModel();
        Map<String, Long> modelTokens = new LinkedHashMap<>();
        for (Object[] row : tokensByModel) {
            modelTokens.put((String) row[0], (Long) row[1]);
        }
        summary.put("tokensByModel", modelTokens);

        // Average response time by model (global)
        List<Object[]> avgResponseTimes = usageMetricRepository.avgResponseTimeByModel();
        Map<String, Double> modelResponseTimes = new LinkedHashMap<>();
        for (Object[] row : avgResponseTimes) {
            modelResponseTimes.put((String) row[0], (Double) row[1]);
        }
        summary.put("avgResponseTimeByModel", modelResponseTimes);

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

        List<Map<String, Object>> modelStats = new ArrayList<>();
        Map<String, Long> tokenMap = new HashMap<>();

        for (Object[] row : tokensByModel) {
            tokenMap.put((String) row[0], (Long) row[1]);
        }

        for (Object[] row : avgResponseTimes) {
            String model = (String) row[0];
            Double avgTime = (Double) row[1];

            Map<String, Object> modelStat = new HashMap<>();
            modelStat.put("model", model);
            modelStat.put("avgResponseTimeMs", avgTime);
            modelStat.put("totalTokens", tokenMap.getOrDefault(model, 0L));
            modelStats.add(modelStat);
        }

        stats.put("models", modelStats);
        return stats;
    }
}
