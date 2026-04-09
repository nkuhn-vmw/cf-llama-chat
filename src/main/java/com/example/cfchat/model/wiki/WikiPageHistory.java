package com.example.cfchat.model.wiki;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "wiki_page_history",
       indexes = @Index(name = "idx_wiki_history_page_ver",
                        columnList = "page_id,version DESC"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WikiPageHistory {

    @Id @GeneratedValue
    private UUID id;

    @Column(name = "page_id", nullable = false)
    private UUID pageId;

    @Column(nullable = false)
    private int version;

    @Column(name = "body_md", columnDefinition = "text", nullable = false)
    private String bodyMd;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 32)
    private String kind;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> frontmatter;

    @Column(name = "edited_by", nullable = false, length = 64)
    private String editedBy;  // "agent:<convId>" | "user:<userId>" | "migration"

    @Column(name = "edit_reason", length = 255)
    private String editReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() { this.createdAt = Instant.now(); }
}
