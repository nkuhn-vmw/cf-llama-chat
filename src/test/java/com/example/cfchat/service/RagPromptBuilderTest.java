package com.example.cfchat.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RagPromptBuilderTest {

    private final RagPromptBuilder ragPromptBuilder = new RagPromptBuilder();

    @Test
    void buildPromptWithCitations_withDocuments_includesSourceNumbers() {
        Document doc1 = createDocument("First document content", "report.pdf", 0.95);
        Document doc2 = createDocument("Second document content", "notes.txt", 0.80);

        String result = ragPromptBuilder.buildPromptWithCitations("What is the summary?", List.of(doc1, doc2));

        assertThat(result).contains("[Source 1]");
        assertThat(result).contains("[Source 2]");
        assertThat(result).contains("First document content");
        assertThat(result).contains("Second document content");
        assertThat(result).contains("report.pdf");
        assertThat(result).contains("notes.txt");
    }

    @Test
    void buildPromptWithCitations_withDocuments_includesQuery() {
        Document doc = createDocument("Some content", "file.txt", 0.9);

        String result = ragPromptBuilder.buildPromptWithCitations("Tell me about X", List.of(doc));

        assertThat(result).contains("Question: Tell me about X");
    }

    @Test
    void buildPromptWithCitations_withDocuments_includesRelevancePercentage() {
        Document doc = createDocument("Content", "file.txt", 0.85);

        String result = ragPromptBuilder.buildPromptWithCitations("query", List.of(doc));

        assertThat(result).contains("85% relevance");
    }

    @Test
    void buildPromptWithCitations_withDocuments_includesInstructions() {
        Document doc = createDocument("Content", "file.txt", 0.9);

        String result = ragPromptBuilder.buildPromptWithCitations("query", List.of(doc));

        assertThat(result).contains("Use the following documents to answer the question");
        assertThat(result).contains("[Source N] notation");
        assertThat(result).contains("Answer with [Source N] citations inline");
    }

    @Test
    void buildPromptWithCitations_emptyDocuments_returnsQueryOnly() {
        String result = ragPromptBuilder.buildPromptWithCitations("What is AI?", List.of());

        assertThat(result).isEqualTo("What is AI?");
    }

    @Test
    void buildPromptWithCitations_nullDocuments_returnsQueryOnly() {
        String result = ragPromptBuilder.buildPromptWithCitations("What is AI?", null);

        assertThat(result).isEqualTo("What is AI?");
    }

    @Test
    void buildPromptWithCitations_documentWithoutFilename_usesDefault() {
        Document doc = new Document("Some content");

        String result = ragPromptBuilder.buildPromptWithCitations("query", List.of(doc));

        assertThat(result).contains("document");
    }

    @Test
    void buildPromptWithCitations_documentWithNullScore_defaultsToZero() {
        Document doc = new Document("Content");

        String result = ragPromptBuilder.buildPromptWithCitations("query", List.of(doc));

        assertThat(result).contains("0% relevance");
    }

    @Test
    void buildCitationMetadata_withDocuments_returnsCitationInfoList() {
        Document doc1 = createDocument("Content 1", "report.pdf", 0.95);
        Document doc2 = createDocument("Content 2", "notes.txt", 0.80);

        List<RagPromptBuilder.CitationInfo> result = ragPromptBuilder.buildCitationMetadata(List.of(doc1, doc2));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).sourceNumber()).isEqualTo(1);
        assertThat(result.get(0).documentName()).isEqualTo("report.pdf");
        assertThat(result.get(0).relevance()).isEqualTo(0.95);
        assertThat(result.get(1).sourceNumber()).isEqualTo(2);
        assertThat(result.get(1).documentName()).isEqualTo("notes.txt");
        assertThat(result.get(1).relevance()).isEqualTo(0.80);
    }

    @Test
    void buildCitationMetadata_emptyDocuments_returnsEmptyList() {
        List<RagPromptBuilder.CitationInfo> result = ragPromptBuilder.buildCitationMetadata(List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void buildCitationMetadata_nullDocuments_returnsEmptyList() {
        List<RagPromptBuilder.CitationInfo> result = ragPromptBuilder.buildCitationMetadata(null);

        assertThat(result).isEmpty();
    }

    @Test
    void buildCitationMetadata_documentWithoutFilename_usesDefault() {
        Document doc = new Document("Content without filename metadata");

        List<RagPromptBuilder.CitationInfo> result = ragPromptBuilder.buildCitationMetadata(List.of(doc));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).documentName()).isEqualTo("document");
    }

    @Test
    void buildCitationMetadata_documentWithNullScore_defaultsToZero() {
        Document doc = new Document("Content");

        List<RagPromptBuilder.CitationInfo> result = ragPromptBuilder.buildCitationMetadata(List.of(doc));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).relevance()).isEqualTo(0.0);
    }

    @Test
    void buildCitationMetadata_sourceNumbersAreOneBased() {
        Document doc1 = createDocument("A", "a.txt", 0.9);
        Document doc2 = createDocument("B", "b.txt", 0.8);
        Document doc3 = createDocument("C", "c.txt", 0.7);

        List<RagPromptBuilder.CitationInfo> result = ragPromptBuilder.buildCitationMetadata(List.of(doc1, doc2, doc3));

        assertThat(result).extracting(RagPromptBuilder.CitationInfo::sourceNumber)
                .containsExactly(1, 2, 3);
    }

    private Document createDocument(String content, String filename, double score) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("filename", filename);
        return Document.builder()
                .text(content)
                .metadata(metadata)
                .score(score)
                .build();
    }
}
