// src/main/java/com/example/cfchat/service/wiki/WikiEmbeddingService.java
package com.example.cfchat.service.wiki;

import com.example.cfchat.dto.wiki.WikiSearchHit;
import com.example.cfchat.model.wiki.WikiPage;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class WikiEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(WikiEmbeddingService.class);

    private final VectorStore vectorStore;

    private static final int DEFAULT_CHUNK_SIZE = 350;
    private static final int DEFAULT_MIN_CHARS = 5;
    private static final int DEFAULT_MAX_CHUNKS = 10_000;

    @Value("${app.documents.chunk-size:350}")
    private int chunkSize = DEFAULT_CHUNK_SIZE;

    @Value("${app.documents.chunk-overlap:100}")
    private int chunkOverlap;

    public WikiEmbeddingService(@Autowired(required = false) VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void indexPage(WikiPage page) {
        if (vectorStore == null) {
            log.warn("VectorStore not available — cannot index wiki page {}", page.getId());
            return;
        }
        // Remove any existing embeddings for this page first
        deletePageEmbeddings(page.getId());

        int effectiveChunkSize = chunkSize > 0 ? chunkSize : DEFAULT_CHUNK_SIZE;
        TokenTextSplitter splitter = new TokenTextSplitter(
            effectiveChunkSize, effectiveChunkSize / 2, DEFAULT_MIN_CHARS, DEFAULT_MAX_CHUNKS, true);

        Document base = new Document(
            "# " + page.getTitle() + "\n\n" + page.getBodyMd(),
            buildMetadata(page));

        List<Document> chunks = splitter.apply(List.of(base));
        if (chunks.isEmpty()) return;
        vectorStore.add(chunks);
    }

    public void deletePageEmbeddings(UUID pageId) {
        if (vectorStore == null) {
            log.warn("VectorStore not available — cannot delete embeddings for wiki page {}", pageId);
            return;
        }
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        Filter.Expression expr = b.eq("pageId", pageId.toString()).build();
        vectorStore.delete(expr);
    }

    public List<WikiSearchHit> search(UUID userId, String query, String kindFilter, int k) {
        if (vectorStore == null) {
            log.warn("VectorStore not available — cannot search wiki embeddings");
            return List.of();
        }
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        Filter.Expression expr;
        if (kindFilter != null && !kindFilter.isBlank()) {
            expr = b.and(
                b.eq("userId", userId.toString()),
                b.eq("kind", kindFilter.trim().toUpperCase())
            ).build();
        } else {
            expr = b.eq("userId", userId.toString()).build();
        }

        SearchRequest req = SearchRequest.builder()
            .query(query)
            .topK(Math.max(1, Math.min(k, 20)))
            .filterExpression(expr)
            .build();

        List<Document> docs = vectorStore.similaritySearch(req);
        if (docs == null) return List.of();

        // Group by pageId, keep best-scoring chunk per page
        Map<String, Document> bestByPage = new LinkedHashMap<>();
        for (Document d : docs) {
            String pageId = String.valueOf(d.getMetadata().get("pageId"));
            bestByPage.putIfAbsent(pageId, d);
        }

        List<WikiSearchHit> hits = new ArrayList<>();
        for (Document d : bestByPage.values()) {
            String pageIdStr = String.valueOf(d.getMetadata().get("pageId"));
            hits.add(new WikiSearchHit(
                UUID.fromString(pageIdStr),
                String.valueOf(d.getMetadata().get("slug")),
                String.valueOf(d.getMetadata().get("title")),
                String.valueOf(d.getMetadata().get("kind")),
                snippet(d.getText()),
                d.getScore() == null ? 0.0 : d.getScore()
            ));
        }
        return hits;
    }

    private Map<String, Object> buildMetadata(WikiPage p) {
        Map<String, Object> md = new HashMap<>();
        md.put("pageId", p.getId().toString());
        md.put("userId", p.getUserId().toString());
        md.put("slug", p.getSlug());
        md.put("title", p.getTitle());
        md.put("kind", p.getKind());
        if (p.getWorkspaceId() != null) md.put("workspaceId", p.getWorkspaceId().toString());
        return md;
    }

    private String snippet(String text) {
        if (text == null) return "";
        String trimmed = text.strip().replaceAll("\\s+", " ");
        return trimmed.length() <= 200 ? trimmed : trimmed.substring(0, 197) + "...";
    }
}
