// src/main/java/com/example/cfchat/model/wiki/WikiLink.java
package com.example.cfchat.model.wiki;

import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wiki_link",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_wiki_link_edge",
           columnNames = {"from_page_id", "to_page_id", "relation"}),
       indexes = {
           @Index(name = "idx_wiki_link_from", columnList = "from_page_id"),
           @Index(name = "idx_wiki_link_to",   columnList = "to_page_id")
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WikiLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "from_page_id", nullable = false)
    private UUID fromPageId;

    @Column(name = "to_page_id", nullable = false)
    private UUID toPageId;

    @Column(nullable = false, length = 32)
    @Pattern(regexp = "mentions|see_also|supersedes|refines|contradicts")
    private String relation;

    @Column(name = "valid_from")
    private Instant validFrom;   // Phase C

    @Column(name = "valid_until")
    private Instant validUntil;  // Phase C

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
