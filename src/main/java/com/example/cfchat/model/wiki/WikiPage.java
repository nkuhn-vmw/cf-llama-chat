package com.example.cfchat.model.wiki;

import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OptimisticLock;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "wiki_page",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_wiki_page_user_slug",
           columnNames = {"user_id", "slug"}),
       indexes = {
           @Index(name = "idx_wiki_page_user_kind", columnList = "user_id,kind"),
           @Index(name = "idx_wiki_page_user_updated", columnList = "user_id,updated_at"),
           @Index(name = "idx_wiki_page_embed_status", columnList = "embedding_status")
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WikiPage {

    @Id @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "workspace_id")  // nullable — Phase C
    private UUID workspaceId;

    @Column(nullable = false, length = 255)
    private String slug;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 32)
    @Pattern(regexp = "ENTITY|CONCEPT|FACT|PREFERENCE|DECISION|EVENT|INDEX|LOG|NOTE")
    private String kind;

    @Column(nullable = false, length = 32)
    @Pattern(regexp = "AGENT_WRITE|MIGRATED_NOTE|MIGRATED_MEMORY|USER_DIRECT_EDIT")
    private String origin;

    @Column(name = "body_md", columnDefinition = "text", nullable = false)
    private String bodyMd;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> frontmatter;

    @Column(name = "source_conversation_id")
    private UUID sourceConversationId;

    @Version
    private int version;

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    // Embedding status/error are updated asynchronously after the initial
    // save (we need the generated ID before we can index the content).
    // Exclude them from @Version optimistic locking so the second save
    // doesn't bump the page version — otherwise every new page ends up
    // at v=2 with zero history rows, which then breaks undo().
    @Column(name = "embedding_status", nullable = false, length = 16)
    @Pattern(regexp = "PENDING|READY|FAILED")
    @OptimisticLock(excluded = true)
    private String embeddingStatus;

    @Column(name = "embedding_error", length = 1024)
    @OptimisticLock(excluded = true)
    private String embeddingError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.embeddingStatus == null) this.embeddingStatus = "PENDING";
        if (this.origin == null) this.origin = "AGENT_WRITE";
    }

    @PreUpdate
    protected void onUpdate() { this.updatedAt = Instant.now(); }
}
