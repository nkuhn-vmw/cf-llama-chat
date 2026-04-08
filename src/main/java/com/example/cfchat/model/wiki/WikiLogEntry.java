package com.example.cfchat.model.wiki;

import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wiki_log_entry",
       indexes = @Index(name = "idx_wiki_log_user_ts", columnList = "user_id,ts DESC"))
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

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID u) { this.userId = u; }
    public String getOp() { return op; }
    public void setOp(String op) { this.op = op; }
    public UUID getPageId() { return pageId; }
    public void setPageId(UUID p) { this.pageId = p; }
    public UUID getConversationId() { return conversationId; }
    public void setConversationId(UUID c) { this.conversationId = c; }
    public String getSummary() { return summary; }
    public void setSummary(String s) { this.summary = s; }
    public Instant getTs() { return ts; }
    public void setTs(Instant ts) { this.ts = ts; }
}
