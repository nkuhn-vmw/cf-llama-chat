package com.example.cfchat.dto;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AgenticSearchDtoTest {

    @Test
    void agenticSearchRequest_defaultValues() {
        AgenticSearchRequest request = AgenticSearchRequest.builder()
                .query("test query")
                .build();

        assertThat(request.getQuery()).isEqualTo("test query");
        assertThat(request.getMaxIterations()).isEqualTo(3);
        assertThat(request.getMaxSubQueries()).isEqualTo(3);
        assertThat(request.getTopK()).isEqualTo(5);
        assertThat(request.isIncludeWebSearch()).isFalse();
        assertThat(request.getModel()).isNull();
        assertThat(request.getProvider()).isNull();
        assertThat(request.getConversationId()).isNull();
    }

    @Test
    void agenticSearchRequest_customValues() {
        UUID conversationId = UUID.randomUUID();
        AgenticSearchRequest request = AgenticSearchRequest.builder()
                .query("complex query about AI")
                .maxIterations(5)
                .maxSubQueries(4)
                .topK(10)
                .includeWebSearch(true)
                .model("gpt-4o")
                .provider("openai")
                .conversationId(conversationId)
                .build();

        assertThat(request.getQuery()).isEqualTo("complex query about AI");
        assertThat(request.getMaxIterations()).isEqualTo(5);
        assertThat(request.getMaxSubQueries()).isEqualTo(4);
        assertThat(request.getTopK()).isEqualTo(10);
        assertThat(request.isIncludeWebSearch()).isTrue();
        assertThat(request.getModel()).isEqualTo("gpt-4o");
        assertThat(request.getProvider()).isEqualTo("openai");
        assertThat(request.getConversationId()).isEqualTo(conversationId);
    }

    @Test
    void agenticSearchResponse_builder() {
        AgenticSearchResponse.SearchSource source = AgenticSearchResponse.SearchSource.builder()
                .query("sub query")
                .sourceType("document")
                .title("test.pdf")
                .snippet("relevant content here")
                .relevance(0.85)
                .build();

        AgenticSearchResponse.SearchIteration iteration = AgenticSearchResponse.SearchIteration.builder()
                .iteration(1)
                .subQueries(List.of("sub query"))
                .sources(List.of(source))
                .intermediateSummary("Found relevant information about the topic.")
                .iterationTimeMs(500L)
                .build();

        AgenticSearchResponse response = AgenticSearchResponse.builder()
                .originalQuery("original question")
                .answer("synthesized answer")
                .htmlAnswer("<p>synthesized answer</p>")
                .iterations(List.of(iteration))
                .totalSourcesFound(1)
                .totalTimeMs(1500L)
                .build();

        assertThat(response.getOriginalQuery()).isEqualTo("original question");
        assertThat(response.getAnswer()).isEqualTo("synthesized answer");
        assertThat(response.getHtmlAnswer()).isEqualTo("<p>synthesized answer</p>");
        assertThat(response.getIterations()).hasSize(1);
        assertThat(response.getTotalSourcesFound()).isEqualTo(1);
        assertThat(response.getTotalTimeMs()).isEqualTo(1500L);
        assertThat(response.getError()).isNull();

        AgenticSearchResponse.SearchIteration iter = response.getIterations().get(0);
        assertThat(iter.getIteration()).isEqualTo(1);
        assertThat(iter.getSubQueries()).containsExactly("sub query");
        assertThat(iter.getSources()).hasSize(1);
        assertThat(iter.getIntermediateSummary()).contains("relevant information");
        assertThat(iter.getIterationTimeMs()).isEqualTo(500L);

        AgenticSearchResponse.SearchSource src = iter.getSources().get(0);
        assertThat(src.getQuery()).isEqualTo("sub query");
        assertThat(src.getSourceType()).isEqualTo("document");
        assertThat(src.getTitle()).isEqualTo("test.pdf");
        assertThat(src.getSnippet()).isEqualTo("relevant content here");
        assertThat(src.getRelevance()).isEqualTo(0.85);
        assertThat(src.getUrl()).isNull();
    }

    @Test
    void agenticSearchResponse_errorCase() {
        AgenticSearchResponse response = AgenticSearchResponse.builder()
                .originalQuery("failed query")
                .error("Search failed: timeout")
                .totalTimeMs(3000L)
                .build();

        assertThat(response.getOriginalQuery()).isEqualTo("failed query");
        assertThat(response.getError()).isEqualTo("Search failed: timeout");
        assertThat(response.getAnswer()).isNull();
        assertThat(response.getTotalTimeMs()).isEqualTo(3000L);
    }

    @Test
    void agenticSearchResponse_webSource() {
        AgenticSearchResponse.SearchSource webSource = AgenticSearchResponse.SearchSource.builder()
                .query("search query")
                .sourceType("web")
                .title("Example Article")
                .url("https://example.com/article")
                .snippet("Web search result snippet")
                .build();

        assertThat(webSource.getSourceType()).isEqualTo("web");
        assertThat(webSource.getUrl()).isEqualTo("https://example.com/article");
        assertThat(webSource.getRelevance()).isNull();
    }

    @Test
    void chatRequest_useAgenticSearchDefaultFalse() {
        ChatRequest request = ChatRequest.builder()
                .message("test")
                .build();

        assertThat(request.isUseAgenticSearch()).isFalse();
    }

    @Test
    void chatRequest_useAgenticSearchSetTrue() {
        ChatRequest request = ChatRequest.builder()
                .message("test")
                .useAgenticSearch(true)
                .build();

        assertThat(request.isUseAgenticSearch()).isTrue();
    }
}
