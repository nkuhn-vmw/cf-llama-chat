package com.example.cfchat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "rag.hybrid.enabled", havingValue = "true")
public class HybridSearchService {
    private final JdbcTemplate jdbc;
    private final VectorStore vectorStore;
    @Value("${rag.top-k:5}") private int topK;
    @Value("${rag.hybrid.bm25-weight:0.3}") private double bm25Weight;
    @Value("${rag.hybrid.vector-weight:0.7}") private double vectorWeight;
    @Value("${rag.hybrid.candidate-multiplier:4}") private int candidateMultiplier;

    public record ScoredChunk(String id, String content, String documentId, double score) {}
    public record RankedDocument(String id, String content, String documentId, double score) {
        public RankedDocument addScore(double add) { return new RankedDocument(id, content, documentId, score + add); }
    }

    public List<RankedDocument> search(String query, UUID userId, boolean includeShared) {
        int candidates = topK * candidateMultiplier;
        List<ScoredChunk> bm25 = bm25Search(query, userId, includeShared, candidates);
        List<ScoredChunk> vector = vectorSearch(query, userId, includeShared, candidates);
        Map<String, RankedDocument> fused = rrf(bm25, vector);
        return fused.values().stream()
            .sorted(Comparator.comparingDouble(RankedDocument::score).reversed())
            .limit(topK).toList();
    }

    private List<ScoredChunk> bm25Search(String query, UUID userId, boolean shared, int limit) {
        try {
            String sql = """
                SELECT id, content, document_id, ts_rank_cd(content_tsv, plainto_tsquery('english', ?)) AS score
                FROM document_chunks WHERE (user_id = ?::text OR (? AND is_shared = TRUE))
                AND content_tsv @@ plainto_tsquery('english', ?) ORDER BY score DESC LIMIT ?""";
            return jdbc.query(sql, new Object[]{query, userId.toString(), shared, query, limit},
                (rs, i) -> new ScoredChunk(rs.getString("id"), rs.getString("content"), rs.getString("document_id"), rs.getDouble("score")));
        } catch (Exception e) {
            log.warn("BM25 search failed (tsv column may not exist yet): {}", e.getMessage());
            return List.of();
        }
    }

    private List<ScoredChunk> vectorSearch(String query, UUID userId, boolean shared, int limit) {
        try {
            String filter = shared
                ? "(user_id == '" + userId + "' || is_shared == 'true')"
                : "user_id == '" + userId + "'";
            return vectorStore.similaritySearch(SearchRequest.builder().query(query).topK(limit).filterExpression(filter).build())
                .stream().map(d -> new ScoredChunk(d.getId(), d.getText(),
                    d.getMetadata().getOrDefault("document_id","").toString(), d.getScore() != null ? d.getScore() : 0.0)).toList();
        } catch (Exception e) {
            log.warn("Vector search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private Map<String, RankedDocument> rrf(List<ScoredChunk> bm25, List<ScoredChunk> vector) {
        int k = 60;
        Map<String, RankedDocument> results = new HashMap<>();
        for (int i = 0; i < bm25.size(); i++) {
            ScoredChunk c = bm25.get(i);
            double s = bm25Weight / (k + i + 1);
            results.merge(c.id(), new RankedDocument(c.id(), c.content(), c.documentId(), s), (a, b) -> a.addScore(b.score()));
        }
        for (int i = 0; i < vector.size(); i++) {
            ScoredChunk c = vector.get(i);
            double s = vectorWeight / (k + i + 1);
            results.merge(c.id(), new RankedDocument(c.id(), c.content(), c.documentId(), s), (a, b) -> a.addScore(b.score()));
        }
        return results;
    }
}
