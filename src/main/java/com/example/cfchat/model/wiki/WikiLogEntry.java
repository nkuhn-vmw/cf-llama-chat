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
@Table(name = "wiki_log_entry",
       indexes = @Index(name = "idx_wiki_log_user_ts", columnList = "user_id,ts DESC"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WikiLogEntry {

    @Id @GeneratedValue private UUID id;

    @Column(name = "user_id", nullable = false) private UUID userId;

    @Column(nullable = false, length = 16)
    @Pattern(regexp = "WRITE|LINK|INVALIDATE|UNDO|READ")
    private String op;

    @Column(name = "page_id")          private UUID pageId;
    @Column(name = "conversation_id")  private UUID conversationId;
    @Column(length = 512)              private String summary;
    @Column(nullable = false)          private Instant ts;

    @PrePersist
    protected void onCreate() { if (ts == null) ts = Instant.now(); }
}
