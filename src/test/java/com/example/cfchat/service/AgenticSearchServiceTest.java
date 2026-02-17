package com.example.cfchat.service;

import com.example.cfchat.config.ChatConfig;
import com.example.cfchat.dto.AgenticSearchRequest;
import com.example.cfchat.dto.AgenticSearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.document.Document;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgenticSearchServiceTest {

    @Mock
    private DocumentEmbeddingService documentEmbeddingService;

    @Mock
    private WebSearchService webSearchService;

    @Mock
    private WebContentService webContentService;

    @Mock
    private MarkdownService markdownService;

    @Mock
    private SystemSettingService systemSettingService;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    private ChatConfig chatConfig;
    private AgenticSearchService agenticSearchService;

    @BeforeEach
    void setUp() {
        chatConfig = new ChatConfig();
        chatConfig.setDefaultProvider("openai");

        agenticSearchService = new AgenticSearchService(
                chatClient,
                null,  // OpenAiChatModel
                null,  // ollamaChatClient
                null,  // ollamaChatModel
                chatConfig,
                null,  // genAiConfig
                null,  // externalBindingService
                documentEmbeddingService,
                webSearchService,
                webContentService,
                markdownService,
                systemSettingService
        );

        ReflectionTestUtils.setField(agenticSearchService, "defaultMaxIterations", 3);
        ReflectionTestUtils.setField(agenticSearchService, "defaultMaxSubQueries", 3);
        ReflectionTestUtils.setField(agenticSearchService, "defaultTopK", 5);
        ReflectionTestUtils.setField(agenticSearchService, "maxSnippetLength", 500);
    }

    @Test
    void isEnabled_whenSystemSettingTrue_returnsTrue() {
        when(systemSettingService.getBooleanSetting("agentic_search_enabled", true))
                .thenReturn(true);

        assertThat(agenticSearchService.isEnabled()).isTrue();
    }

    @Test
    void isEnabled_whenSystemSettingFalse_returnsFalse() {
        when(systemSettingService.getBooleanSetting("agentic_search_enabled", true))
                .thenReturn(false);

        assertThat(agenticSearchService.isEnabled()).isFalse();
    }

    @Test
    void search_whenDisabled_returnsErrorResponse() {
        when(systemSettingService.getBooleanSetting("agentic_search_enabled", true))
                .thenReturn(false);

        AgenticSearchRequest request = AgenticSearchRequest.builder()
                .query("test query")
                .build();

        AgenticSearchResponse response = agenticSearchService.search(request, UUID.randomUUID());

        assertThat(response.getOriginalQuery()).isEqualTo("test query");
        assertThat(response.getError()).isEqualTo("Agentic search is currently disabled");
    }

    @Test
    void search_whenNoChatClient_returnsErrorResponse() {
        when(systemSettingService.getBooleanSetting("agentic_search_enabled", true))
                .thenReturn(true);

        // Create service with no chat clients
        AgenticSearchService serviceNoChatClient = new AgenticSearchService(
                null, null, null, null,
                chatConfig, null, null,
                documentEmbeddingService, webSearchService, webContentService,
                markdownService, systemSettingService
        );
        ReflectionTestUtils.setField(serviceNoChatClient, "defaultMaxIterations", 3);
        ReflectionTestUtils.setField(serviceNoChatClient, "defaultMaxSubQueries", 3);
        ReflectionTestUtils.setField(serviceNoChatClient, "defaultTopK", 5);
        ReflectionTestUtils.setField(serviceNoChatClient, "maxSnippetLength", 500);

        AgenticSearchRequest request = AgenticSearchRequest.builder()
                .query("test query")
                .build();

        AgenticSearchResponse response = serviceNoChatClient.search(request, UUID.randomUUID());

        assertThat(response.getError()).contains("No chat model available");
    }

    @Test
    void search_withNoDocumentsFound_returnsNoResultsMessage() {
        when(systemSettingService.getBooleanSetting("agentic_search_enabled", true))
                .thenReturn(true);
        when(documentEmbeddingService.isAvailable()).thenReturn(true);
        when(documentEmbeddingService.searchUserDocuments(any(), anyString(), anyInt()))
                .thenReturn(List.of());

        // Mock the chatClient chain for query decomposition
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.messages(anyList())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("What is the capital of France?");

        when(markdownService.toHtml(anyString())).thenAnswer(inv -> inv.getArgument(0));

        AgenticSearchRequest request = AgenticSearchRequest.builder()
                .query("What is the capital of France?")
                .maxIterations(1)
                .build();

        AgenticSearchResponse response = agenticSearchService.search(request, UUID.randomUUID());

        assertThat(response.getOriginalQuery()).isEqualTo("What is the capital of France?");
        assertThat(response.getAnswer()).contains("No relevant information was found");
        assertThat(response.getError()).isNull();
        assertThat(response.getTotalTimeMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void search_withDocumentsFound_returnsSynthesizedAnswer() {
        when(systemSettingService.getBooleanSetting("agentic_search_enabled", true))
                .thenReturn(true);
        when(documentEmbeddingService.isAvailable()).thenReturn(true);

        // Return some document chunks
        Document doc1 = new Document("Paris is the capital of France.");
        doc1.getMetadata().put("filename", "geography.pdf");
        doc1.getMetadata().put("user_id", "test-user");
        when(documentEmbeddingService.searchUserDocuments(any(), anyString(), anyInt()))
                .thenReturn(List.of(doc1));

        // Mock chat client for all LLM calls
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.messages(anyList())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);

        // First call: decomposition, second call: intermediate summary,
        // third call: refinement (SUFFICIENT), fourth call: synthesis
        when(callResponseSpec.content())
                .thenReturn("capital of France")          // decomposition
                .thenReturn("Paris is the capital.")       // intermediate summary
                .thenReturn("SUFFICIENT")                  // refinement
                .thenReturn("Paris is the capital of France. [Source 1]");  // synthesis

        when(markdownService.toHtml(anyString())).thenAnswer(inv ->
                "<p>" + inv.getArgument(0) + "</p>");

        AgenticSearchRequest request = AgenticSearchRequest.builder()
                .query("What is the capital of France?")
                .maxIterations(2)
                .build();

        AgenticSearchResponse response = agenticSearchService.search(request, UUID.randomUUID());

        assertThat(response.getOriginalQuery()).isEqualTo("What is the capital of France?");
        assertThat(response.getAnswer()).contains("Paris");
        assertThat(response.getHtmlAnswer()).contains("<p>");
        assertThat(response.getError()).isNull();
        assertThat(response.getTotalSourcesFound()).isGreaterThan(0);
        assertThat(response.getIterations()).isNotEmpty();
    }

    @Test
    void search_respectsMaxIterationsLimit() {
        when(systemSettingService.getBooleanSetting("agentic_search_enabled", true))
                .thenReturn(true);
        when(documentEmbeddingService.isAvailable()).thenReturn(true);
        when(documentEmbeddingService.searchUserDocuments(any(), anyString(), anyInt()))
                .thenReturn(List.of());

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.messages(anyList())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        // Return sub-queries each time to keep iterating
        when(callResponseSpec.content())
                .thenReturn("sub-query 1\nsub-query 2")
                .thenReturn("sub-query 3")
                .thenReturn("sub-query 4")
                .thenReturn("sub-query 5")
                .thenReturn("sub-query 6");

        when(markdownService.toHtml(anyString())).thenAnswer(inv -> inv.getArgument(0));

        AgenticSearchRequest request = AgenticSearchRequest.builder()
                .query("complex question")
                .maxIterations(2)
                .build();

        AgenticSearchResponse response = agenticSearchService.search(request, UUID.randomUUID());

        // Should have at most 2 iterations
        assertThat(response.getIterations()).hasSizeLessThanOrEqualTo(2);
    }

    @Test
    void search_requestPreservesOriginalQuery() {
        when(systemSettingService.getBooleanSetting("agentic_search_enabled", true))
                .thenReturn(true);
        when(documentEmbeddingService.isAvailable()).thenReturn(true);
        when(documentEmbeddingService.searchUserDocuments(any(), anyString(), anyInt()))
                .thenReturn(List.of());

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.messages(anyList())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("sub-query 1");

        when(markdownService.toHtml(anyString())).thenAnswer(inv -> inv.getArgument(0));

        String originalQuery = "Tell me about quantum computing applications in medicine";
        AgenticSearchRequest request = AgenticSearchRequest.builder()
                .query(originalQuery)
                .maxIterations(1)
                .build();

        AgenticSearchResponse response = agenticSearchService.search(request, UUID.randomUUID());

        assertThat(response.getOriginalQuery()).isEqualTo(originalQuery);
    }

    @Test
    void search_recordsTimingInformation() {
        when(systemSettingService.getBooleanSetting("agentic_search_enabled", true))
                .thenReturn(true);
        when(documentEmbeddingService.isAvailable()).thenReturn(true);
        when(documentEmbeddingService.searchUserDocuments(any(), anyString(), anyInt()))
                .thenReturn(List.of());

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.messages(anyList())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("sub-query");

        when(markdownService.toHtml(anyString())).thenAnswer(inv -> inv.getArgument(0));

        AgenticSearchRequest request = AgenticSearchRequest.builder()
                .query("test query")
                .maxIterations(1)
                .build();

        AgenticSearchResponse response = agenticSearchService.search(request, UUID.randomUUID());

        assertThat(response.getTotalTimeMs()).isGreaterThanOrEqualTo(0);
        if (!response.getIterations().isEmpty()) {
            assertThat(response.getIterations().get(0).getIterationTimeMs()).isGreaterThanOrEqualTo(0);
        }
    }
}
