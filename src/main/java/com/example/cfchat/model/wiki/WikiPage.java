package com.example.cfchat.model.wiki;

import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
import org.hibernate.annotations.JdbcTypeCode;
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
    @Column(columnDefinition = "varchar(4000)")
    private Map<String, Object> frontmatter;

    @Column(name = "source_conversation_id")
    private UUID sourceConversationId;

    @Version
    private int version;

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    @Column(name = "embedding_status", nullable = false, length = 16)
    @Pattern(regexp = "PENDING|READY|FAILED")
    private String embeddingStatus;

    @Column(name = "embedding_error", length = 1024)
    private String embeddingError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.embeddingStatus == null) this.embeddingStatus = "PENDING";
        if (this.origin == null) this.origin = "AGENT_WRITE";
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }

    // --- getters / setters ---
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public String getBodyMd() { return bodyMd; }
    public void setBodyMd(String bodyMd) { this.bodyMd = bodyMd; }
    public Map<String, Object> getFrontmatter() { return frontmatter; }
    public void setFrontmatter(Map<String, Object> f) { this.frontmatter = f; }
    public UUID getSourceConversationId() { return sourceConversationId; }
    public void setSourceConversationId(UUID id) { this.sourceConversationId = id; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public Instant getLastReadAt() { return lastReadAt; }
    public void setLastReadAt(Instant t) { this.lastReadAt = t; }
    public String getEmbeddingStatus() { return embeddingStatus; }
    public void setEmbeddingStatus(String s) { this.embeddingStatus = s; }
    public String getEmbeddingError() { return embeddingError; }
    public void setEmbeddingError(String e) { this.embeddingError = e; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
