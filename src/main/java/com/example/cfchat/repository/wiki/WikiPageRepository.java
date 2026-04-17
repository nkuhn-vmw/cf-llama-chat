package com.example.cfchat.repository.wiki;

import com.example.cfchat.model.wiki.WikiPage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WikiPageRepository extends JpaRepository<WikiPage, UUID> {

    Optional<WikiPage> findByUserIdAndSlug(UUID userId, String slug);

    List<WikiPage> findByUserIdOrderByUpdatedAtDesc(UUID userId, Pageable pageable);

    List<WikiPage> findByUserIdAndKindOrderByUpdatedAtDesc(UUID userId, String kind);

    long countByUserId(UUID userId);

    long countByUserIdAndOrigin(UUID userId, String origin);

    @Query("""
        SELECT new com.example.cfchat.repository.wiki.WikiPageIndexRow(p.id, p.slug, p.title, p.kind)
        FROM WikiPage p
        WHERE p.userId = :userId
          AND p.kind <> 'INDEX' AND p.kind <> 'LOG'
        ORDER BY
          CASE WHEN p.lastReadAt IS NULL THEN 1 ELSE 0 END,
          p.lastReadAt DESC,
          p.updatedAt DESC
        """)
    List<WikiPageIndexRow> findTopForIndex(@Param("userId") UUID userId, Pageable pageable);

    default List<WikiPageIndexRow> findTopForIndex(UUID userId, int limit) {
        return findTopForIndex(userId, Pageable.ofSize(limit));
    }

    List<WikiPage> findByEmbeddingStatusIn(List<String> statuses, Pageable pageable);

    /**
     * Candidate IDs for embedding retry: PENDING/FAILED, plus CLAIMED rows whose
     * claim is older than the stale cutoff (worker died mid-index).
     */
    @Query("""
        SELECT p.id FROM WikiPage p
        WHERE p.embeddingStatus IN ('PENDING','FAILED')
           OR (p.embeddingStatus = 'CLAIMED' AND (p.embeddingClaimedAt IS NULL OR p.embeddingClaimedAt < :staleCutoff))
        """)
    List<UUID> findRetryCandidateIds(@Param("staleCutoff") Instant staleCutoff, Pageable pageable);

    /**
     * Atomic claim. Returns 1 iff this caller transitioned the row into CLAIMED;
     * concurrent callers on other instances get 0 and must skip.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE WikiPage p
           SET p.embeddingStatus = 'CLAIMED',
               p.embeddingClaimedAt = :now
         WHERE p.id = :id
           AND (p.embeddingStatus IN ('PENDING','FAILED')
                OR (p.embeddingStatus = 'CLAIMED'
                    AND (p.embeddingClaimedAt IS NULL OR p.embeddingClaimedAt < :staleCutoff)))
        """)
    int claimForIndexing(@Param("id") UUID id,
                         @Param("now") Instant now,
                         @Param("staleCutoff") Instant staleCutoff);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE WikiPage p
           SET p.embeddingStatus = 'READY',
               p.embeddingError = null,
               p.embeddingClaimedAt = null
         WHERE p.id = :id
        """)
    int markReady(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE WikiPage p
           SET p.embeddingStatus = 'FAILED',
               p.embeddingError = :err,
               p.embeddingClaimedAt = null
         WHERE p.id = :id
        """)
    int markFailed(@Param("id") UUID id, @Param("err") String err);
}
