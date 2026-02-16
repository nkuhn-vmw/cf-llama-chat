package com.example.cfchat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.IntStream;

@Service
@Slf4j
public class RagPromptBuilder {

    public record CitationInfo(int sourceNumber, String documentName, double relevance) {}

    /**
     * Build a RAG prompt with numbered source citations.
     */
    public String buildPromptWithCitations(String query, List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return query;
        }

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
}
