package com.example.cfchat.repository.wiki;

import com.example.cfchat.model.wiki.WikiPage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
