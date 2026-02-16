package com.example.cfchat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.IntStream;

/**
 * Builds RAG prompts with citation support.
 * Supports two retrieval modes:
 *   - "snippet" (default): uses chunk-based retrieval, each chunk is a separate source
 *   - "full": when a chunk matches, all chunks from the same source document are grouped together
 */
@Service
@Slf4j
public class RagPromptBuilder {

    @Value("${rag.retrieval-mode:snippet}")
    private String defaultRetrievalMode;

    public record CitationInfo(int sourceNumber, String documentName, double relevance) {}

    /**
     * Build a RAG prompt with numbered source citations using the default retrieval mode.
     */
    public String buildPromptWithCitations(String query, List<Document> documents) {
        return buildPromptWithCitations(query, documents, null);
    }

    /**
     * Build a RAG prompt with numbered source citations.
     * @param retrievalMode "snippet" for chunk-based, "full" for whole-document grouping, or null for default
     */
    public String buildPromptWithCitations(String query, List<Document> documents, String retrievalMode) {
        if (documents == null || documents.isEmpty()) {
            return query;
        }

        String mode = (retrievalMode != null) ? retrievalMode : defaultRetrievalMode;

        if ("full".equalsIgnoreCase(mode)) {
            return buildFullDocumentPrompt(query, documents);
        }

        return buildSnippetPrompt(query, documents);
    }

    /**
     * Snippet mode: each chunk is rendered as its own numbered source (original behavior).
     */
    private String buildSnippetPrompt(String query, List<Document> documents) {
        StringBuilder sb = new StringBuilder();
        sb.append("Use the following documents to answer the question. ");
        sb.append("When you use information from a document, cite it using [Source N] notation. ");
        sb.append("If the documents don't contain relevant information, say so.\n\n");

        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            String filename = (String) doc.getMetadata().getOrDefault("filename", "document");
            double score = doc.getScore() != null ? doc.getScore() : 0.0;

            sb.append(String.format("[Source %d] (%.0f%% relevance) From '%s':\n",
                    i + 1, score * 100, filename));
            sb.append(doc.getText());
            sb.append("\n\n");
        }

        sb.append("Question: ").append(query);
        sb.append("\nAnswer with [Source N] citations inline where you use information from the sources.");

        return sb.toString();
    }

    /**
     * Full-document mode: chunks from the same source document are grouped into a single source entry.
     * The document is identified by the "document_id" metadata field.
     * Chunks within each document are ordered by chunk_index.
     */
    private String buildFullDocumentPrompt(String query, List<Document> documents) {
        StringBuilder sb = new StringBuilder();
        sb.append("Use the following documents to answer the question. ");
        sb.append("When you use information from a document, cite it using [Source N] notation. ");
        sb.append("If the documents don't contain relevant information, say so.\n\n");

        // Group chunks by document_id, preserving insertion order
        Map<String, List<Document>> grouped = new LinkedHashMap<>();
        for (Document doc : documents) {
            String docId = (String) doc.getMetadata().getOrDefault("document_id", "unknown");
            grouped.computeIfAbsent(docId, k -> new ArrayList<>()).add(doc);
        }

        int sourceIndex = 1;
        for (Map.Entry<String, List<Document>> entry : grouped.entrySet()) {
            List<Document> chunks = entry.getValue();

            // Sort chunks by chunk_index for coherent ordering
            chunks.sort(Comparator.comparingInt(d -> {
                Object idx = d.getMetadata().get("chunk_index");
                return (idx instanceof Number) ? ((Number) idx).intValue() : 0;
            }));

            // Use the filename from the first chunk as the document name
            String filename = (String) chunks.get(0).getMetadata().getOrDefault("filename", "document");

            // Compute average relevance score across all matched chunks
            double avgScore = chunks.stream()
                    .mapToDouble(d -> d.getScore() != null ? d.getScore() : 0.0)
                    .average()
                    .orElse(0.0);

            sb.append(String.format("[Source %d] (%.0f%% relevance) Full document '%s':\n",
                    sourceIndex, avgScore * 100, filename));

            // Concatenate all chunks for this document
            for (Document chunk : chunks) {
                sb.append(chunk.getText());
                sb.append("\n");
            }
            sb.append("\n");
            sourceIndex++;
        }

        sb.append("Question: ").append(query);
        sb.append("\nAnswer with [Source N] citations inline where you use information from the sources.");

        return sb.toString();
    }

    /**
     * Build a RAG prompt that includes YouTube transcript context alongside document sources.
     * The transcript is prepended as an additional source before any document results.
     */
    public String buildPromptWithTranscript(String query, String transcript, String videoUrl,
                                            List<Document> documents) {
        StringBuilder sb = new StringBuilder();
        sb.append("Use the following sources to answer the question. ");
        sb.append("When you use information from a source, cite it using [Source N] notation. ");
        sb.append("If the sources don't contain relevant information, say so.\n\n");

        int sourceIndex = 1;

        // Add YouTube transcript as the first source
        if (transcript != null && !transcript.isEmpty()) {
            sb.append(String.format("[Source %d] From YouTube video '%s':\n", sourceIndex, videoUrl));
            sb.append(transcript);
            sb.append("\n\n");
            sourceIndex++;
        }

        // Add document sources
        if (documents != null) {
            for (Document doc : documents) {
                String filename = (String) doc.getMetadata().getOrDefault("filename", "document");
                double score = doc.getScore() != null ? doc.getScore() : 0.0;

                sb.append(String.format("[Source %d] (%.0f%% relevance) From '%s':\n",
                        sourceIndex, score * 100, filename));
                sb.append(doc.getText());
                sb.append("\n\n");
                sourceIndex++;
            }
        }

        sb.append("Question: ").append(query);
        sb.append("\nAnswer with [Source N] citations inline where you use information from the sources.");

        return sb.toString();
    }

    /**
     * Build citation metadata for frontend rendering.
     */
    public List<CitationInfo> buildCitationMetadata(List<Document> documents) {
        if (documents == null) return List.of();

        return IntStream.range(0, documents.size())
                .mapToObj(i -> {
                    Document doc = documents.get(i);
                    String filename = (String) doc.getMetadata().getOrDefault("filename", "document");
                    double score = doc.getScore() != null ? doc.getScore() : 0.0;
                    return new CitationInfo(i + 1, filename, score);
                })
                .toList();
    }

    /**
     * Get the configured default retrieval mode.
     */
    public String getDefaultRetrievalMode() {
        return defaultRetrievalMode;
    }
}
