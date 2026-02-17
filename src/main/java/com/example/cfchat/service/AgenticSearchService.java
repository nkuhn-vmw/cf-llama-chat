package com.example.cfchat.service;

import com.example.cfchat.config.ChatConfig;
import com.example.cfchat.config.GenAiConfig;
import com.example.cfchat.dto.AgenticSearchRequest;
import com.example.cfchat.dto.AgenticSearchResponse;
import com.example.cfchat.dto.AgenticSearchResponse.SearchIteration;
import com.example.cfchat.dto.AgenticSearchResponse.SearchSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Agentic Search Service that orchestrates multi-step RAG search.
 * <p>
 * The agent performs the following loop:
 * <ol>
 *   <li>Decomposes a complex query into focused sub-queries using the LLM</li>
 *   <li>Executes each sub-query against the vector store (and optionally web search)</li>
 *   <li>Evaluates whether enough information has been gathered</li>
 *   <li>If not, generates refined follow-up sub-queries for another iteration</li>
 *   <li>Synthesizes all gathered information into a final comprehensive answer</li>
 * </ol>
 * <p>
 * Enabled via the system setting {@code agentic_search_enabled} or the
 * configuration property {@code agentic-search.enabled}.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "agentic-search.enabled", havingValue = "true", matchIfMissing = false)
public class AgenticSearchService {

    private final ChatClient primaryChatClient;
    private final ChatModel primaryChatModel;
    private final ChatClient ollamaChatClient;
    private final OllamaChatModel ollamaChatModel;
    private final ChatConfig chatConfig;
    private final GenAiConfig genAiConfig;
    private final ExternalBindingService externalBindingService;
    private final DocumentEmbeddingService documentEmbeddingService;
    private final WebSearchService webSearchService;
    private final WebContentService webContentService;
    private final MarkdownService markdownService;
    private final SystemSettingService systemSettingService;

    @Value("${agentic-search.max-iterations:3}")
    private int defaultMaxIterations;

    @Value("${agentic-search.max-sub-queries:3}")
    private int defaultMaxSubQueries;

    @Value("${agentic-search.top-k:5}")
    private int defaultTopK;

    @Value("${agentic-search.max-snippet-length:500}")
    private int maxSnippetLength;

    private static final String DECOMPOSITION_SYSTEM_PROMPT = """
            You are a search query decomposition assistant. Your job is to break down a complex question \
            into simpler, focused sub-queries that can be used to search a document knowledge base.

            Rules:
            - Generate between 1 and %d sub-queries
            - Each sub-query should target a specific aspect of the original question
            - Sub-queries should be concise and search-friendly (no full sentences)
            - If the question is already simple enough, return just the original question
            - Return ONLY the sub-queries, one per line, with no numbering, bullets, or extra text
            """;

    private static final String REFINEMENT_SYSTEM_PROMPT = """
            You are a search refinement assistant. Based on the original question and the information \
            gathered so far, determine if more searching is needed.

            If the gathered information is sufficient to answer the original question, respond with exactly: SUFFICIENT

            If more information is needed, generate between 1 and %d refined sub-queries that target \
            the gaps in the current information. Return ONLY the sub-queries, one per line, with no \
            numbering, bullets, or extra text. Do not repeat queries that have already been tried.
            """;

    private static final String SYNTHESIS_SYSTEM_PROMPT = """
            You are a research synthesis assistant. Based on the original question and all the information \
            gathered from multiple search passes, provide a comprehensive, well-structured answer.

            Rules:
            - Cite sources using [Source N] notation where N matches the source number provided
            - If sources contain conflicting information, note the discrepancy
            - If the gathered information does not fully answer the question, clearly state what is unknown
            - Use Markdown formatting for readability (headings, lists, bold, etc.)
            - Be thorough but concise
            """;

    private static final String INTERMEDIATE_SUMMARY_PROMPT = """
            Briefly summarize the key findings from the following search results in 2-3 sentences. \
            Focus on the most relevant information that helps answer the original question.
            """;

    public AgenticSearchService(
            @Autowired(required = false) ChatClient primaryChatClient,
            @Autowired(required = false) OpenAiChatModel openAiChatModel,
            @Autowired(required = false) @Qualifier("ollamaChatClient") ChatClient ollamaChatClient,
            @Autowired(required = false) OllamaChatModel ollamaChatModel,
            ChatConfig chatConfig,
            @Autowired(required = false) GenAiConfig genAiConfig,
            @Autowired(required = false) ExternalBindingService externalBindingService,
            @Autowired(required = false) DocumentEmbeddingService documentEmbeddingService,
            @Autowired(required = false) WebSearchService webSearchService,
            @Autowired(required = false) WebContentService webContentService,
            MarkdownService markdownService,
            @Autowired(required = false) SystemSettingService systemSettingService) {
        this.primaryChatClient = primaryChatClient;
        this.primaryChatModel = openAiChatModel;
        this.ollamaChatClient = ollamaChatClient;
        this.ollamaChatModel = ollamaChatModel;
        this.chatConfig = chatConfig;
        this.genAiConfig = genAiConfig;
        this.externalBindingService = externalBindingService;
        this.documentEmbeddingService = documentEmbeddingService;
        this.webSearchService = webSearchService;
        this.webContentService = webContentService;
        this.markdownService = markdownService;
        this.systemSettingService = systemSettingService;

        log.info("AgenticSearchService initialized - vectorStore: {}, webSearch: {}",
                documentEmbeddingService != null && documentEmbeddingService.isAvailable(),
                webSearchService != null && webSearchService.isEnabled());
    }

    /**
     * Check whether agentic search is enabled via system settings.
     */
    public boolean isEnabled() {
        if (systemSettingService == null) {
            return true; // If no system settings service, rely on ConditionalOnProperty
        }
        return systemSettingService.getBooleanSetting("agentic_search_enabled", true);
    }

    /**
     * Execute a multi-step agentic search.
     *
     * @param request the search request containing the query and configuration
     * @param userId  the ID of the user executing the search (for document filtering)
     * @return the search response with synthesized answer and iteration details
     */
    public AgenticSearchResponse search(AgenticSearchRequest request, UUID userId) {
        if (!isEnabled()) {
            return AgenticSearchResponse.builder()
                    .originalQuery(request.getQuery())
                    .error("Agentic search is currently disabled")
                    .build();
        }

        long startTime = System.currentTimeMillis();
        String query = request.getQuery();
        int maxIterations = Math.min(request.getMaxIterations(), defaultMaxIterations);
        int maxSubQueries = Math.min(request.getMaxSubQueries(), defaultMaxSubQueries);
        int topK = request.getTopK() > 0 ? request.getTopK() : defaultTopK;

        log.info("Starting agentic search: query='{}', maxIterations={}, maxSubQueries={}, topK={}, userId={}",
                truncate(query, 100), maxIterations, maxSubQueries, topK, userId);

        ChatClient chatClient = resolveChatClient(request.getProvider(), request.getModel());
        if (chatClient == null) {
            return AgenticSearchResponse.builder()
                    .originalQuery(query)
                    .error("No chat model available for the specified provider/model")
                    .totalTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }

        List<SearchIteration> iterations = new ArrayList<>();
        List<SearchSource> allSources = new ArrayList<>();
        Set<String> triedQueries = new HashSet<>();

        try {
            for (int i = 0; i < maxIterations; i++) {
                long iterStart = System.currentTimeMillis();
                int iterationNum = i + 1;

                // Step 1: Decompose or refine queries
                List<String> subQueries;
                if (i == 0) {
                    subQueries = decomposeQuery(chatClient, query, maxSubQueries);
                } else {
                    String gathered = buildGatheredContext(allSources);
                    subQueries = refineQueries(chatClient, query, gathered, triedQueries, maxSubQueries);

                    // If the model says SUFFICIENT, stop iterating
                    if (subQueries.isEmpty()) {
                        log.info("Agentic search: model determined information is sufficient after {} iterations", i);
                        break;
                    }
                }

                triedQueries.addAll(subQueries);
                log.debug("Iteration {}: sub-queries = {}", iterationNum, subQueries);

                // Step 2: Execute searches for each sub-query
                List<SearchSource> iterSources = new ArrayList<>();
                for (String subQuery : subQueries) {
                    // Search vector store (user documents)
                    List<SearchSource> docSources = searchVectorStore(userId, subQuery, topK);
                    iterSources.addAll(docSources);

                    // Optionally search the web
                    if (request.isIncludeWebSearch() && webSearchService != null && webSearchService.isEnabled()) {
                        List<SearchSource> webSources = searchWeb(subQuery);
                        iterSources.addAll(webSources);
                    }
                }

                // Deduplicate sources
                List<SearchSource> uniqueSources = deduplicateSources(iterSources, allSources);
                allSources.addAll(uniqueSources);

                // Step 3: Generate intermediate summary
                String intermediateSummary = null;
                if (!uniqueSources.isEmpty()) {
                    intermediateSummary = generateIntermediateSummary(chatClient, query, uniqueSources);
                }

                long iterTime = System.currentTimeMillis() - iterStart;
                iterations.add(SearchIteration.builder()
                        .iteration(iterationNum)
                        .subQueries(subQueries)
                        .sources(uniqueSources)
                        .intermediateSummary(intermediateSummary)
                        .iterationTimeMs(iterTime)
                        .build());

                log.info("Iteration {} complete: {} new sources found in {}ms",
                        iterationNum, uniqueSources.size(), iterTime);
            }

            // Step 4: Synthesize final answer from all gathered sources
            String answer;
            if (allSources.isEmpty()) {
                answer = "No relevant information was found for your query across " +
                        iterations.size() + " search iteration(s). " +
                        "Try rephrasing your question or uploading relevant documents.";
            } else {
                answer = synthesizeAnswer(chatClient, query, allSources);
            }

            long totalTime = System.currentTimeMillis() - startTime;
            log.info("Agentic search complete: {} iterations, {} total sources, {}ms",
                    iterations.size(), allSources.size(), totalTime);

            return AgenticSearchResponse.builder()
                    .originalQuery(query)
                    .answer(answer)
                    .htmlAnswer(markdownService.toHtml(answer))
                    .iterations(iterations)
                    .totalSourcesFound(allSources.size())
                    .totalTimeMs(totalTime)
                    .build();

        } catch (Exception e) {
            log.error("Agentic search failed for query '{}': {}", truncate(query, 100), e.getMessage(), e);
            long totalTime = System.currentTimeMillis() - startTime;
            return AgenticSearchResponse.builder()
                    .originalQuery(query)
                    .error("Search failed: " + e.getMessage())
                    .iterations(iterations)
                    .totalSourcesFound(allSources.size())
                    .totalTimeMs(totalTime)
                    .build();
        }
    }

    /**
     * Decompose a complex query into simpler sub-queries using the LLM.
     */
    private List<String> decomposeQuery(ChatClient chatClient, String query, int maxSubQueries) {
        String systemPrompt = String.format(DECOMPOSITION_SYSTEM_PROMPT, maxSubQueries);

        String response = chatClient.prompt()
                .messages(List.of(
                        new SystemMessage(systemPrompt),
                        new UserMessage("Original question: " + query)
                ))
                .call()
                .content();

        return parseSubQueries(response, maxSubQueries);
    }

    /**
     * Refine queries based on what has been gathered so far.
     * Returns an empty list if the model determines enough information is available.
     */
    private List<String> refineQueries(ChatClient chatClient, String originalQuery,
                                        String gatheredContext, Set<String> triedQueries,
                                        int maxSubQueries) {
        String systemPrompt = String.format(REFINEMENT_SYSTEM_PROMPT, maxSubQueries);

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Original question: ").append(originalQuery).append("\n\n");
        userPrompt.append("Information gathered so far:\n").append(gatheredContext).append("\n\n");
        userPrompt.append("Queries already tried:\n");
        for (String q : triedQueries) {
            userPrompt.append("- ").append(q).append("\n");
        }

        String response = chatClient.prompt()
                .messages(List.of(
                        new SystemMessage(systemPrompt),
                        new UserMessage(userPrompt.toString())
                ))
                .call()
                .content();

        if (response != null && response.trim().equalsIgnoreCase("SUFFICIENT")) {
            return List.of();
        }

        return parseSubQueries(response, maxSubQueries);
    }

    /**
     * Parse sub-queries from an LLM response (one per line).
     */
    private List<String> parseSubQueries(String response, int maxSubQueries) {
        if (response == null || response.isBlank()) {
            return List.of();
        }

        return Arrays.stream(response.split("\n"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                // Strip common prefixes like "1.", "- ", "* "
                .map(line -> line.replaceFirst("^\\d+[.)\\s]+", ""))
                .map(line -> line.replaceFirst("^[-*]\\s+", ""))
                .map(String::trim)
                .filter(line -> !line.isEmpty() && line.length() > 2)
                .limit(maxSubQueries)
                .collect(Collectors.toList());
    }

    /**
     * Search the vector store for documents matching a sub-query.
     */
    private List<SearchSource> searchVectorStore(UUID userId, String query, int topK) {
        if (documentEmbeddingService == null || !documentEmbeddingService.isAvailable()) {
            log.debug("Vector store not available for agentic search");
            return List.of();
        }

        try {
            List<Document> results = documentEmbeddingService.searchUserDocuments(userId, query, topK);

            return results.stream()
                    .map(doc -> {
                        String filename = (String) doc.getMetadata().getOrDefault("filename", "document");
                        double score = doc.getScore() != null ? doc.getScore() : 0.0;
                        String text = doc.getText();
                        if (text != null && text.length() > maxSnippetLength) {
                            text = text.substring(0, maxSnippetLength) + "...";
                        }

                        return SearchSource.builder()
                                .query(query)
                                .sourceType("document")
                                .title(filename)
                                .snippet(text)
                                .relevance(score)
                                .build();
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Vector store search failed for query '{}': {}", truncate(query, 80), e.getMessage());
            return List.of();
        }
    }

    /**
     * Search the web for results matching a sub-query.
     */
    private List<SearchSource> searchWeb(String query) {
        if (webSearchService == null || !webSearchService.isEnabled()) {
            return List.of();
        }

        try {
            List<WebSearchService.SearchResult> results = webSearchService.search(query, 3);

            return results.stream()
                    .map(result -> SearchSource.builder()
                            .query(query)
                            .sourceType("web")
                            .title(result.title())
                            .url(result.url())
                            .snippet(result.snippet() != null && result.snippet().length() > maxSnippetLength
                                    ? result.snippet().substring(0, maxSnippetLength) + "..."
                                    : result.snippet())
                            .build())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Web search failed for query '{}': {}", truncate(query, 80), e.getMessage());
            return List.of();
        }
    }

    /**
     * Deduplicate sources by comparing titles and snippets.
     */
    private List<SearchSource> deduplicateSources(List<SearchSource> newSources,
                                                   List<SearchSource> existingSources) {
        Set<String> existingKeys = existingSources.stream()
                .map(this::sourceKey)
                .collect(Collectors.toSet());

        return newSources.stream()
                .filter(source -> {
                    String key = sourceKey(source);
                    return existingKeys.add(key); // returns false if already present
                })
                .collect(Collectors.toList());
    }

    /**
     * Generate a unique key for a source to detect duplicates.
     */
    private String sourceKey(SearchSource source) {
        String title = source.getTitle() != null ? source.getTitle() : "";
        String snippet = source.getSnippet() != null ? source.getSnippet() : "";
        // Use first 100 chars of snippet for comparison
        String snippetPrefix = snippet.length() > 100 ? snippet.substring(0, 100) : snippet;
        return source.getSourceType() + ":" + title + ":" + snippetPrefix;
    }

    /**
     * Build a text representation of gathered sources for the refinement prompt.
     */
    private String buildGatheredContext(List<SearchSource> sources) {
        if (sources.isEmpty()) {
            return "No information gathered yet.";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sources.size(); i++) {
            SearchSource source = sources.get(i);
            sb.append(String.format("[Source %d] (%s) '%s':\n",
                    i + 1, source.getSourceType(), source.getTitle()));
            if (source.getSnippet() != null) {
                sb.append(source.getSnippet());
            }
            sb.append("\n\n");
        }
        return sb.toString();
    }

    /**
     * Generate an intermediate summary of search results for an iteration.
     */
    private String generateIntermediateSummary(ChatClient chatClient, String originalQuery,
                                                List<SearchSource> sources) {
        try {
            StringBuilder context = new StringBuilder();
            context.append("Original question: ").append(originalQuery).append("\n\n");
            context.append("Search results:\n");
            for (SearchSource source : sources) {
                context.append("- From '").append(source.getTitle()).append("': ");
                if (source.getSnippet() != null) {
                    String snippet = source.getSnippet().length() > 200
                            ? source.getSnippet().substring(0, 200) + "..."
                            : source.getSnippet();
                    context.append(snippet);
                }
                context.append("\n");
            }

            return chatClient.prompt()
                    .messages(List.of(
                            new SystemMessage(INTERMEDIATE_SUMMARY_PROMPT),
                            new UserMessage(context.toString())
                    ))
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("Failed to generate intermediate summary: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Synthesize a final answer from all gathered sources.
     */
    private String synthesizeAnswer(ChatClient chatClient, String originalQuery,
                                     List<SearchSource> allSources) {
        StringBuilder context = new StringBuilder();
        context.append("Original question: ").append(originalQuery).append("\n\n");
        context.append("Gathered sources:\n\n");

        for (int i = 0; i < allSources.size(); i++) {
            SearchSource source = allSources.get(i);
            context.append(String.format("[Source %d] (%s) '%s'",
                    i + 1, source.getSourceType(), source.getTitle()));
            if (source.getUrl() != null) {
                context.append(" - ").append(source.getUrl());
            }
            context.append(":\n");
            if (source.getSnippet() != null) {
                context.append(source.getSnippet());
            }
            context.append("\n\n");
        }

        context.append("Please provide a comprehensive answer to the original question using all relevant sources above.");

        return chatClient.prompt()
                .messages(List.of(
                        new SystemMessage(SYNTHESIS_SYSTEM_PROMPT),
                        new UserMessage(context.toString())
                ))
                .call()
                .content();
    }

    /**
     * Resolve the appropriate ChatClient based on provider and model.
     * Follows the same pattern as ChatService.
     */
    private ChatClient resolveChatClient(String provider, String modelName) {
        if (provider == null) {
            provider = chatConfig.getDefaultProvider();
        }

        if ("ollama".equalsIgnoreCase(provider)) {
            return ollamaChatClient;
        }

        // Check external bindings
        if (externalBindingService != null && modelName != null) {
            if ("external".equalsIgnoreCase(provider) || externalBindingService.hasModel(modelName)) {
                ChatModel externalModel = externalBindingService.getChatModelByName(modelName);
                if (externalModel != null) {
                    log.debug("Using external binding model for agentic search: {}", modelName);
                    return ChatClient.builder(externalModel).build();
                }
            }
        }

        // For GenAI, try to get the specific model
        if ("genai".equalsIgnoreCase(provider) && modelName != null && genAiConfig != null) {
            ChatClient modelClient = genAiConfig.getChatClientForModel(modelName);
            if (modelClient != null) {
                log.debug("Using GenAI model for agentic search: {}", modelName);
                return modelClient;
            }
        }

        return primaryChatClient;
    }

    /**
     * Truncate a string for logging purposes.
     */
    private String truncate(String text, int maxLength) {
        if (text == null) return "null";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
