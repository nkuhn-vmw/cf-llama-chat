package com.example.cfchat.service.wiki;

import com.example.cfchat.model.wiki.WikiPage;
import com.example.cfchat.repository.wiki.WikiPageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class WikiEmbeddingRetryJob {

    private static final Logger log = LoggerFactory.getLogger(WikiEmbeddingRetryJob.class);
    private static final int BATCH = 20;

    private final WikiPageRepository pageRepo;
    private final WikiEmbeddingService embeddingService;

    public WikiEmbeddingRetryJob(WikiPageRepository pageRepo, WikiEmbeddingService embeddingService) {
        this.pageRepo = pageRepo;
        this.embeddingService = embeddingService;
    }

    @Scheduled(fixedDelayString = "${app.wiki.embedding.retry.interval-ms:300000}")
    @Transactional
    public void retryPending() {
        List<WikiPage> pending = pageRepo.findByEmbeddingStatusIn(
                List.of("PENDING", "FAILED"), PageRequest.of(0, BATCH));
        if (pending.isEmpty()) return;
        log.info("Retrying embeddings for {} wiki pages", pending.size());
        for (WikiPage p : pending) {
            try {
                embeddingService.indexPage(p);
                p.setEmbeddingStatus("READY");
                p.setEmbeddingError(null);
            } catch (Exception e) {
                log.warn("Embedding retry failed for page {}: {}", p.getId(), e.getMessage());
                p.setEmbeddingStatus("FAILED");
                p.setEmbeddingError(String.valueOf(e.getMessage()));
            }
            pageRepo.save(p);
        }
    }
}
