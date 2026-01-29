package com.example.cfchat.repository;

import com.example.cfchat.model.UsageMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface UsageMetricRepository extends JpaRepository<UsageMetric, UUID> {

    List<UsageMetric> findByUserIdOrderByTimestampDesc(UUID userId);

    List<UsageMetric> findByUserIdAndTimestampAfterOrderByTimestampDesc(UUID userId, LocalDateTime after);

    List<UsageMetric> findByTimestampAfterOrderByTimestampDesc(LocalDateTime after);

    @Query("SELECT SUM(m.totalTokens) FROM UsageMetric m WHERE m.userId = :userId")
    Long sumTotalTokensByUserId(UUID userId);

    @Query("SELECT SUM(m.totalTokens) FROM UsageMetric m WHERE m.userId = :userId AND m.timestamp >= :after")
    Long sumTotalTokensByUserIdAndTimestampAfter(UUID userId, LocalDateTime after);

    @Query("SELECT SUM(m.totalTokens) FROM UsageMetric m WHERE m.timestamp >= :after")
    Long sumTotalTokensByTimestampAfter(LocalDateTime after);

    @Query("SELECT m.model, SUM(m.totalTokens) FROM UsageMetric m WHERE m.userId = :userId GROUP BY m.model")
    List<Object[]> sumTokensByModelForUser(UUID userId);

    @Query("SELECT m.model, SUM(m.totalTokens) FROM UsageMetric m GROUP BY m.model")
    List<Object[]> sumTokensByModel();

    @Query("SELECT m.model, AVG(m.responseTimeMs) FROM UsageMetric m WHERE m.responseTimeMs IS NOT NULL GROUP BY m.model")
    List<Object[]> avgResponseTimeByModel();

    @Query("SELECT m.model, AVG(m.responseTimeMs) FROM UsageMetric m WHERE m.userId = :userId AND m.responseTimeMs IS NOT NULL GROUP BY m.model")
    List<Object[]> avgResponseTimeByModelForUser(UUID userId);

    @Query("SELECT m.userId, SUM(m.totalTokens) FROM UsageMetric m GROUP BY m.userId ORDER BY SUM(m.totalTokens) DESC")
    List<Object[]> sumTokensByUser();

    long countByUserId(UUID userId);

    long countByTimestampAfter(LocalDateTime after);

    long countByUserIdAndTimestampAfter(UUID userId, LocalDateTime after);
}
