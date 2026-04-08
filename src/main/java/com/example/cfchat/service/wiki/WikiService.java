// src/main/java/com/example/cfchat/service/wiki/WikiService.java
package com.example.cfchat.service.wiki;

import com.example.cfchat.dto.wiki.WikiIndexEntry;
import com.example.cfchat.dto.wiki.WikiOpPayload;
import com.example.cfchat.dto.wiki.WikiPageView;
import com.example.cfchat.dto.wiki.WikiSearchHit;
import com.example.cfchat.event.WikiOpEvent;
import com.example.cfchat.model.wiki.*;
import com.example.cfchat.repository.wiki.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class WikiService {

    private final WikiPageRepository pageRepo;
    private final WikiPageHistoryRepository historyRepo;
    private final WikiLinkRepository linkRepo;
    private final WikiLogRepository logRepo;
    private final WikiEmbeddingService embeddingService;
    private final ApplicationEventPublisher publisher;
    private final WikiContextLoader contextLoader;

    public WikiService(WikiPageRepository pageRepo,
                       WikiPageHistoryRepository historyRepo,
                       WikiLinkRepository linkRepo,
                       WikiLogRepository logRepo,
                       WikiEmbeddingService embeddingService,
                       ApplicationEventPublisher publisher,
                       WikiContextLoader contextLoader) {
        this.pageRepo = pageRepo;
        this.historyRepo = historyRepo;
        this.linkRepo = linkRepo;
        this.logRepo = logRepo;
        this.embeddingService = embeddingService;
        this.publisher = publisher;
        this.contextLoader = contextLoader;
    }

    @Transactional
    public WikiPageView upsert(WikiScope scope, String slug, String title,
                               String kindRaw, String bodyMd, String origin) {
        WikiKind.parse(kindRaw);  // validate

        WikiPage page = pageRepo.findByUserIdAndSlug(scope.userId(), slug).orElse(null);
        boolean isNew = (page == null);

        if (!isNew) {
            snapshotToHistory(page, editedByFromScope(scope), null);
            page.setTitle(title);
            page.setKind(kindRaw);
            page.setBodyMd(bodyMd);
            page.setEmbeddingStatus("PENDING");
            page.setEmbeddingError(null);
        } else {
            page = new WikiPage();
            page.setUserId(scope.userId());
            page.setSlug(slug);
            page.setTitle(title);
            page.setKind(kindRaw);
            page.setOrigin(origin == null ? "AGENT_WRITE" : origin);
            page.setBodyMd(bodyMd);
            page.setSourceConversationId(scope.conversationId());
            page.setEmbeddingStatus("PENDING");
        }

        page = pageRepo.save(page);

        // Synchronous embedding in Phase B — simple, testable, acceptable latency.
        try {
            embeddingService.indexPage(page);
            page.setEmbeddingStatus("READY");
            page.setEmbeddingError(null);
        } catch (Exception e) {
            page.setEmbeddingStatus("FAILED");
            page.setEmbeddingError(String.valueOf(e.getMessage()));
            // The retry job will pick it up; we still commit the page row.
        }
        pageRepo.save(page);

        logOp(scope, "WRITE", page.getId(), "Saved " + page.getSlug());
        contextLoader.invalidate(scope.userId());
        publisher.publishEvent(new WikiOpEvent(this, scope.userId(), scope.conversationId(),
            new WikiOpPayload("WRITE", page.getId(), page.getSlug(), page.getTitle(),
                              page.getKind(), "Saved " + page.getSlug())));

        return toView(page);
    }

    @Transactional
    public WikiPageView read(WikiScope scope, String slug) {
        WikiPage page = requireOwned(scope, pageRepo.findByUserIdAndSlug(scope.userId(), slug)
            .orElseThrow(() -> new NoSuchElementException("No wiki page: " + slug)));
        page.setLastReadAt(Instant.now());
        pageRepo.save(page);
        logOp(scope, "READ", page.getId(), "Read " + page.getSlug());
        return toView(page);
    }

    @Transactional
    public void link(WikiScope scope, String fromSlug, String toSlug, String relation) {
        WikiPage from = requireOwned(scope, pageRepo.findByUserIdAndSlug(scope.userId(), fromSlug)
            .orElseThrow(() -> new NoSuchElementException("No wiki page: " + fromSlug)));
        WikiPage to = requireOwned(scope, pageRepo.findByUserIdAndSlug(scope.userId(), toSlug)
            .orElseThrow(() -> new NoSuchElementException("No wiki page: " + toSlug)));

        linkRepo.findByFromPageIdAndToPageIdAndRelation(from.getId(), to.getId(), relation)
            .ifPresentOrElse(
                existing -> { /* already linked — idempotent */ },
                () -> {
                    WikiLink l = new WikiLink();
                    l.setFromPageId(from.getId());
                    l.setToPageId(to.getId());
                    l.setRelation(relation);
                    l.setCreatedBy(editedByFromScope(scope));
                    linkRepo.save(l);
                });

        logOp(scope, "LINK", from.getId(),
              "Linked " + from.getSlug() + " -[" + relation + "]-> " + to.getSlug());
        publisher.publishEvent(new WikiOpEvent(this, scope.userId(), scope.conversationId(),
            new WikiOpPayload("LINK", from.getId(), from.getSlug(), from.getTitle(),
                              from.getKind(), "Linked to " + to.getSlug())));
    }

    @Transactional
    public void invalidate(WikiScope scope, String slug, String reason) {
        WikiPage page = requireOwned(scope, pageRepo.findByUserIdAndSlug(scope.userId(), slug)
            .orElseThrow(() -> new NoSuchElementException("No wiki page: " + slug)));

        snapshotToHistory(page, editedByFromScope(scope), reason);

        Map<String,Object> fm = page.getFrontmatter() == null
            ? new HashMap<>() : new HashMap<>(page.getFrontmatter());
        fm.put("invalidated_at", Instant.now().toString());
        fm.put("invalidated_reason", reason);
        page.setFrontmatter(fm);
        pageRepo.save(page);

        embeddingService.deletePageEmbeddings(page.getId());

        logOp(scope, "INVALIDATE", page.getId(), "Invalidated " + page.getSlug() + ": " + reason);
        contextLoader.invalidate(scope.userId());
        publisher.publishEvent(new WikiOpEvent(this, scope.userId(), scope.conversationId(),
            new WikiOpPayload("INVALIDATE", page.getId(), page.getSlug(), page.getTitle(),
                              page.getKind(), "Invalidated: " + reason)));
    }

    @Transactional
    public WikiPageView undo(WikiScope scope, UUID pageId) {
        WikiPage page = requireOwned(scope, pageRepo.findById(pageId)
            .orElseThrow(() -> new NoSuchElementException("No wiki page: " + pageId)));
        WikiPageHistory prior = historyRepo.findFirstByPageIdOrderByVersionDesc(pageId)
            .orElseThrow(() -> new IllegalStateException("No history for " + pageId));

        // Snapshot the current state first so undo is itself undoable
        snapshotToHistory(page, editedByFromScope(scope), "undo");

        page.setTitle(prior.getTitle());
        page.setBodyMd(prior.getBodyMd());
        if (prior.getKind() != null) page.setKind(prior.getKind());
        page.setFrontmatter(prior.getFrontmatter());
        page.setEmbeddingStatus("PENDING");
        pageRepo.save(page);
        try {
            embeddingService.indexPage(page);
            page.setEmbeddingStatus("READY");
        } catch (Exception e) {
            page.setEmbeddingStatus("FAILED");
            page.setEmbeddingError(String.valueOf(e.getMessage()));
        }
        pageRepo.save(page);

        logOp(scope, "UNDO", pageId, "Undid " + page.getSlug());
        contextLoader.invalidate(scope.userId());
        publisher.publishEvent(new WikiOpEvent(this, scope.userId(), scope.conversationId(),
            new WikiOpPayload("UNDO", page.getId(), page.getSlug(), page.getTitle(),
                              page.getKind(), "Undone")));
        return toView(page);
    }

    @Transactional(readOnly = true)
    public List<WikiSearchHit> search(WikiScope scope, String query, String kind, int k) {
        return embeddingService.search(scope.userId(), query, kind, k);
    }

    @Transactional(readOnly = true)
    public List<WikiIndexEntry> indexFor(WikiScope scope, String kindFilter) {
        List<WikiPageIndexRow> rows = pageRepo.findTopForIndex(scope.userId(), 200);
        return rows.stream()
            .filter(r -> kindFilter == null || kindFilter.isBlank()
                         || r.kind().equalsIgnoreCase(kindFilter.trim()))
            .map(r -> new WikiIndexEntry(r.id(), r.slug(), r.title(), r.kind()))
            .toList();
    }

    // ---------- helpers ----------

    private void snapshotToHistory(WikiPage page, String editedBy, String reason) {
        WikiPageHistory h = new WikiPageHistory();
        h.setPageId(page.getId());
        h.setVersion(page.getVersion());
        h.setTitle(page.getTitle());
        h.setBodyMd(page.getBodyMd());
        h.setKind(page.getKind());
        h.setFrontmatter(page.getFrontmatter());
        h.setEditedBy(editedBy);
        h.setEditReason(reason);
        historyRepo.save(h);
    }

    private void logOp(WikiScope scope, String op, UUID pageId, String summary) {
        WikiLogEntry e = new WikiLogEntry();
        e.setUserId(scope.userId());
        e.setConversationId(scope.conversationId());
        e.setOp(op);
        e.setPageId(pageId);
        e.setSummary(summary);
        logRepo.save(e);
    }

    private WikiPage requireOwned(WikiScope scope, WikiPage p) {
        if (!p.getUserId().equals(scope.userId())) {
            throw new SecurityException("Wiki page is not owned by this user");
        }
        return p;
    }

    private String editedByFromScope(WikiScope scope) {
        return scope.conversationId() != null
            ? "agent:" + scope.conversationId()
            : "user:" + scope.userId();
    }

    private WikiPageView toView(WikiPage p) {
        return new WikiPageView(
            p.getId(), p.getUserId(), p.getSlug(), p.getTitle(), p.getKind(), p.getOrigin(),
            p.getBodyMd(), p.getVersion(), p.getCreatedAt(), p.getUpdatedAt(),
            p.getEmbeddingStatus());
    }
}
