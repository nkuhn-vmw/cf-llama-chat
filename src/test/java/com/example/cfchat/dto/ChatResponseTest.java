package com.example.cfchat.dto;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChatResponseTest {

    @Test
    void builder_allFields_setCorrectly() {
        UUID convId = UUID.randomUUID();
        UUID msgId = UUID.randomUUID();

        ChatResponse response = ChatResponse.builder()
                .conversationId(convId)
                .messageId(msgId)
                .content("Hello world")
                .htmlContent("<p>Hello world</p>")
                .model("gpt-4o")
                .error(null)
                .streaming(false)
                .complete(true)
                .timeToFirstTokenMs(250L)
                .tokensPerSecond(45.5)
                .totalResponseTimeMs(1500L)
                .build();

        assertThat(response.getConversationId()).isEqualTo(convId);
        assertThat(response.getMessageId()).isEqualTo(msgId);
        assertThat(response.getContent()).isEqualTo("Hello world");
        assertThat(response.getHtmlContent()).isEqualTo("<p>Hello world</p>");
        assertThat(response.getModel()).isEqualTo("gpt-4o");
        assertThat(response.getError()).isNull();
        assertThat(response.isStreaming()).isFalse();
        assertThat(response.isComplete()).isTrue();
        assertThat(response.getTimeToFirstTokenMs()).isEqualTo(250L);
        assertThat(response.getTokensPerSecond()).isEqualTo(45.5);
        assertThat(response.getTotalResponseTimeMs()).isEqualTo(1500L);
    }

    @Test
    void builder_streamingResponse_fieldsCorrect() {
        ChatResponse response = ChatResponse.builder()
                .content("partial")
                .streaming(true)
                .complete(false)
                .build();

        assertThat(response.isStreaming()).isTrue();
        assertThat(response.isComplete()).isFalse();
    }

    @Test
    void builder_errorResponse_hasErrorField() {
        ChatResponse response = ChatResponse.builder()
                .error("Model unavailable")
                .build();

        assertThat(response.getError()).isEqualTo("Model unavailable");
        assertThat(response.getContent()).isNull();
    }

    @Test
    void noArgsConstructor_defaults() {
        ChatResponse response = new ChatResponse();

        assertThat(response.isStreaming()).isFalse();
        assertThat(response.isComplete()).isFalse();
        assertThat(response.getContent()).isNull();
    }

    @Test
    void builder_withCitations_buildsCitationList() {
        ChatResponse.CitationMeta citation = ChatResponse.CitationMeta.builder()
                .sourceNumber(1)
                .documentName("test.pdf")
                .relevance(0.95)
                .build();

        ChatResponse response = ChatResponse.builder()
                .conversationId(UUID.randomUUID())
                .content("Test [Source 1]")
                .htmlContent("<p>Test [Source 1]</p>")
                .citations(List.of(citation))
                .complete(true)
                .build();

        assertThat(response.getCitations()).hasSize(1);
        assertThat(response.getCitations().get(0).getSourceNumber()).isEqualTo(1);
        assertThat(response.getCitations().get(0).getDocumentName()).isEqualTo("test.pdf");
        assertThat(response.getCitations().get(0).getRelevance()).isEqualTo(0.95);
    }

    @Test
    void builder_withoutCitations_citationsIsNull() {
        ChatResponse response = ChatResponse.builder()
                .content("Hello")
                .complete(true)
                .build();

        assertThat(response.getCitations()).isNull();
    }

    @Test
    void builder_withMultipleCitations_allPresent() {
        ChatResponse.CitationMeta citation1 = ChatResponse.CitationMeta.builder()
                .sourceNumber(1)
                .documentName("report.pdf")
                .relevance(0.95)
                .build();
        ChatResponse.CitationMeta citation2 = ChatResponse.CitationMeta.builder()
                .sourceNumber(2)
                .documentName("notes.txt")
                .relevance(0.80)
                .build();

        ChatResponse response = ChatResponse.builder()
                .content("Answer with [Source 1] and [Source 2]")
                .citations(List.of(citation1, citation2))
                .complete(true)
                .build();

        assertThat(response.getCitations()).hasSize(2);
        assertThat(response.getCitations().get(0).getDocumentName()).isEqualTo("report.pdf");
        assertThat(response.getCitations().get(1).getDocumentName()).isEqualTo("notes.txt");
    }

    @Test
    void builder_withPerformanceMetrics_setsAllFields() {
        ChatResponse response = ChatResponse.builder()
                .timeToFirstTokenMs(150L)
                .tokensPerSecond(45.5)
                .totalResponseTimeMs(2000L)
                .temporary(true)
                .build();

        assertThat(response.getTimeToFirstTokenMs()).isEqualTo(150L);
        assertThat(response.getTokensPerSecond()).isEqualTo(45.5);
        assertThat(response.getTotalResponseTimeMs()).isEqualTo(2000L);
        assertThat(response.isTemporary()).isTrue();
    }

    @Test
    void citationMeta_noArgsConstructor_defaults() {
        ChatResponse.CitationMeta citation = new ChatResponse.CitationMeta();

        assertThat(citation.getSourceNumber()).isEqualTo(0);
        assertThat(citation.getDocumentName()).isNull();
        assertThat(citation.getRelevance()).isEqualTo(0.0);
    }

    @Test
    void citationMeta_allArgsConstructor_setsFields() {
        ChatResponse.CitationMeta citation = new ChatResponse.CitationMeta(3, "data.csv", 0.72);

        assertThat(citation.getSourceNumber()).isEqualTo(3);
        assertThat(citation.getDocumentName()).isEqualTo("data.csv");
        assertThat(citation.getRelevance()).isEqualTo(0.72);
    }
}
