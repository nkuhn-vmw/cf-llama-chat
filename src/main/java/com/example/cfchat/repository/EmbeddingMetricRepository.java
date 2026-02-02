package com.example.cfchat.repository;

import com.example.cfchat.model.EmbeddingMetric;
import com.example.cfchat.model.EmbeddingMetric.OperationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface EmbeddingMetricRepository extends JpaRepository<EmbeddingMetric, UUID> {

    // Count queries
    long countByUserId(UUID userId);

    long countByUserIdAndTimestampAfter(UUID userId, LocalDateTime timestamp);

    long countByTimestampAfter(LocalDateTime timestamp);

    long countByOperationType(OperationType operationType);

    // Sum queries for chunks
    @Query("SELECT COALESCE(SUM(e.chunkCount), 0) FROM EmbeddingMetric e WHERE e.userId = :userId")
    Long sumChunksByUserId(@Param("userId") UUID userId);

    @Query("SELECT COALESCE(SUM(e.chunkCount), 0) FROM EmbeddingMetric e WHERE e.userId = :userId AND e.timestamp > :timestamp")
    Long sumChunksByUserIdAndTimestampAfter(@Param("userId") UUID userId, @Param("timestamp") LocalDateTime timestamp);

    @Query("SELECT COALESCE(SUM(e.chunkCount), 0) FROM EmbeddingMetric e WHERE e.timestamp > :timestamp")
    Long sumChunksByTimestampAfter(@Param("timestamp") LocalDateTime timestamp);

    @Query("SELECT COALESCE(SUM(e.chunkCount), 0) FROM EmbeddingMetric e")
    Long sumTotalChunks();

    // Sum queries for characters
    @Query("SELECT COALESCE(SUM(e.totalCharacters), 0) FROM EmbeddingMetric e WHERE e.userId = :userId")
    Long sumCharactersByUserId(@Param("userId") UUID userId);

    @Query("SELECT COALESCE(SUM(e.totalCharacters), 0) FROM EmbeddingMetric e WHERE e.timestamp > :timestamp")
    Long sumCharactersByTimestampAfter(@Param("timestamp") LocalDateTime timestamp);

    @Query("SELECT COALESCE(SUM(e.totalCharacters), 0) FROM EmbeddingMetric e")
    Long sumTotalCharacters();

    // Average processing time
    @Query("SELECT AVG(e.processingTimeMs) FROM EmbeddingMetric e WHERE e.userId = :userId")
    Double avgProcessingTimeByUserId(@Param("userId") UUID userId);

    @Query("SELECT AVG(e.processingTimeMs) FROM EmbeddingMetric e")
    Double avgProcessingTime();

    @Query("SELECT AVG(e.processingTimeMs) FROM EmbeddingMetric e WHERE e.model = :model")
    Double avgProcessingTimeByModel(@Param("model") String model);

    // Group by model
    @Query("SELECT e.model, SUM(e.chunkCount) FROM EmbeddingMetric e GROUP BY e.model ORDER BY SUM(e.chunkCount) DESC")
    List<Object[]> sumChunksByModel();

    @Query("SELECT e.model, SUM(e.chunkCount) FROM EmbeddingMetric e WHERE e.userId = :userId GROUP BY e.model ORDER BY SUM(e.chunkCount) DESC")
    List<Object[]> sumChunksByModelForUser(@Param("userId") UUID userId);

    @Query("SELECT e.model, SUM(e.chunkCount) FROM EmbeddingMetric e WHERE e.timestamp > :timestamp GROUP BY e.model ORDER BY SUM(e.chunkCount) DESC")
    List<Object[]> sumChunksByModelAfter(@Param("timestamp") LocalDateTime timestamp);

    @Query("SELECT e.model, COUNT(e) FROM EmbeddingMetric e GROUP BY e.model ORDER BY COUNT(e) DESC")
    List<Object[]> countRequestsByModel();

    @Query("SELECT e.model, AVG(e.processingTimeMs) FROM EmbeddingMetric e GROUP BY e.model ORDER BY AVG(e.processingTimeMs)")
    List<Object[]> avgProcessingTimeByModelAll();

    // Group by user
    @Query("SELECT e.userId, SUM(e.chunkCount) FROM EmbeddingMetric e GROUP BY e.userId ORDER BY SUM(e.chunkCount) DESC")
    List<Object[]> sumChunksByUser();

    // Recent metrics
    List<EmbeddingMetric> findByUserIdOrderByTimestampDesc(UUID userId);
}
