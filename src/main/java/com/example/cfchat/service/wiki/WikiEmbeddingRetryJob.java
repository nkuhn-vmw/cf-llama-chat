package com.example.cfchat.service.wiki;

import com.example.cfchat.model.wiki.WikiPage;
import com.example.cfchat.repository.wiki.WikiPageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Retries embedding for wiki pages left in PENDING/FAILED (or stuck in CLAIMED).
 *
 * Multi-instance safe: each row is claimed via an atomic
 * UPDATE ... WHERE id=? AND status IN (...) before indexing, so two app
 * instances scheduling in parallel won't double-index. A claim that isn't
 * released within {@code staleClaimMs} is considered abandoned (worker crashed)
 * and another instance may reclaim it.
 */
@Component
public class WikiEmbeddingRetryJob {

    private static final Logger log = LoggerFactory.getLogger(WikiEmbeddingRetryJob.class);
    private static final int BATCH = 20;

    private final WikiPageRepository pageRepo;
    private final WikiEmbeddingService embeddingService;
    private final WikiEmbeddingRetryJob self;

    @Value("${app.wiki.embedding.claim-stale-ms:600000}")
    private long staleClaimMs;

    public WikiEmbeddingRetryJob(WikiPageRepository pageRepo,
                                 WikiEmbeddingService embeddingService,
                                 @Lazy WikiEmbeddingRetryJob self) {
        this.pageRepo = pageRepo;
        this.embeddingService = embeddingService;
        this.self = self;
    }

    @Scheduled(fixedDelayString = "${app.wiki.embedding.retry.interval-ms:300000}")
    public void retryPending() {
        Instant staleCutoff = Instant.now().minus(Duration.ofMillis(staleClaimMs));
        List<UUID> ids = pageRepo.findRetryCandidateIds(staleCutoff, PageRequest.of(0, BATCH));
        if (ids.isEmpty()) return;
        log.info("Considering {} wiki pages for embedding retry", ids.size());
        int indexed = 0;
        int skipped = 0;
        for (UUID id : ids) {
            Optional<WikiPage> claimed = self.tryClaim(id);
            if (claimed.isEmpty()) {
                skipped++;
                continue;
            }
            WikiPage p = claimed.get();
            try {
                embeddingService.indexPage(p);
                self.markReady(id);
                indexed++;
            } catch (Exception e) {
                log.warn("Embedding retry failed for page {}: {}", id, e.getMessage());
                self.markFailed(id, String.valueOf(e.getMessage()));
            }
        }
        if (indexed > 0 || skipped > 0) {
            log.info("Wiki embedding retry: indexed={}, skipped-by-peer={}", indexed, skipped);
        }
    }

    @Transactional
    public Optional<WikiPage> tryClaim(UUID id) {
        Instant now = Instant.now();
        Instant staleCutoff = now.minus(Duration.ofMillis(staleClaimMs));
        int claimed = pageRepo.claimForIndexing(id, now, staleCutoff);
        return claimed == 1 ? pageRepo.findById(id) : Optional.empty();
    }

    @Transactional
    public void markReady(UUID id) {
        pageRepo.markReady(id);
    }

    @Transactional
    public void markFailed(UUID id, String err) {
        String truncated = err == null ? null : err.substring(0, Math.min(err.length(), 1024));
        pageRepo.markFailed(id, truncated);
    }
}
