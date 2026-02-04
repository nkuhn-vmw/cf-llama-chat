package com.example.cfchat.service;

import com.example.cfchat.auth.UserService;
import com.example.cfchat.config.ChatConfig;
import com.example.cfchat.dto.ChatRequest;
import com.example.cfchat.dto.ChatResponse;
import com.example.cfchat.mcp.McpToolCallbackCacheService;
import com.example.cfchat.model.Conversation;
import com.example.cfchat.model.Message;
import com.example.cfchat.model.ModelInfo;
import com.example.cfchat.model.Skill;
import com.example.cfchat.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import com.example.cfchat.config.GenAiConfig;
import com.example.cfchat.service.ExternalBindingService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class ChatService {

    private final ChatClient primaryChatClient;
    private final ChatModel primaryChatModel;
    private final ChatClient ollamaChatClient;
    private final OllamaChatModel ollamaChatModel;
    private final ConversationService conversationService;
    private final MarkdownService markdownService;
    private final ChatConfig chatConfig;
    private final UserService userService;
    private final MetricsService metricsService;
    private final GenAiConfig genAiConfig;
    private final SkillService skillService;
    private final McpToolCallbackCacheService mcpToolCallbackCacheService;
    private final DocumentEmbeddingService documentEmbeddingService;
    private final ExternalBindingService externalBindingService;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @Value("${app.documents.rag-top-k:5}")
    private int ragTopK;

    public ChatService(
            @Autowired(required = false) ChatClient primaryChatClient,
            @Autowired(required = false) OpenAiChatModel openAiChatModel,
            @Autowired(required = false) @Qualifier("ollamaChatClient") ChatClient ollamaChatClient,
            @Autowired(required = false) OllamaChatModel ollamaChatModel,
            ConversationService conversationService,
            MarkdownService markdownService,
            ChatConfig chatConfig,
            UserService userService,
            MetricsService metricsService,
            @Autowired(required = false) GenAiConfig genAiConfig,
            @Autowired(required = false) SkillService skillService,
            @Autowired(required = false) McpToolCallbackCacheService mcpToolCallbackCacheService,
            @Autowired(required = false) DocumentEmbeddingService documentEmbeddingService,
            @Autowired(required = false) ExternalBindingService externalBindingService) {
        this.primaryChatClient = primaryChatClient;
        // Use OpenAI model as primary for streaming
        this.primaryChatModel = openAiChatModel;
        this.ollamaChatClient = ollamaChatClient;
        this.ollamaChatModel = ollamaChatModel;
        this.conversationService = conversationService;
        this.markdownService = markdownService;
        this.chatConfig = chatConfig;
        this.userService = userService;
        this.metricsService = metricsService;
        this.genAiConfig = genAiConfig;
        this.skillService = skillService;
        this.mcpToolCallbackCacheService = mcpToolCallbackCacheService;
        this.documentEmbeddingService = documentEmbeddingService;
        this.externalBindingService = externalBindingService;

        log.info("ChatService initialized - primaryChatClient: {}, ollamaChatClient: {}, primaryChatModel: {}, mcpTools: {}, documentEmbedding: {}, externalBindings: {}",
                primaryChatClient != null, ollamaChatClient != null,
                openAiChatModel != null ? openAiChatModel.getClass().getSimpleName() : "null",
                mcpToolCallbackCacheService != null ? mcpToolCallbackCacheService.getMcpServerServices().size() : 0,
                documentEmbeddingService != null && documentEmbeddingService.isAvailable(),
                externalBindingService != null);
    }

    public ChatResponse chat(ChatRequest request) {
        String provider = request.getProvider() != null ? request.getProvider() : chatConfig.getDefaultProvider();
        String model = request.getModel();

        // Get current user ID
        UUID userId = userService.getCurrentUser().map(User::getId).orElse(null);

        // Create or get conversation
        UUID conversationId = request.getConversationId();
        Conversation conversation;

        if (conversationId == null) {
            conversation = conversationService.createConversation(null, provider, model, userId);
            conversationId = conversation.getId();
        } else {
            if (userId != null) {
                conversation = conversationService.getConversationEntityForUser(conversationId, userId)
                        .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
            } else {
                conversation = conversationService.getConversationEntity(conversationId)
                        .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
            }
        }

        // Save user message
        conversationService.addMessage(conversationId, Message.MessageRole.USER, request.getMessage(), null);

        // Build prompt with conversation history (with optional skill and document context)
        List<org.springframework.ai.chat.messages.Message> messages = buildMessageHistory(
            conversation, request.getMessage(), request.getSkillId(), userId, request.isUseDocumentContext());

        // Get AI response - pass model name for GenAI multi-model support
        ChatClient chatClient = getChatClient(provider, model);
        if (chatClient == null) {
            throw new IllegalStateException("No chat client available for provider: " + provider);
        }

        long startTime = System.currentTimeMillis();

        // Build the chat request with optional MCP tools
        var promptSpec = chatClient.prompt().messages(messages);

        // Add MCP tools if available and enabled
        if (request.isUseTools() && mcpToolCallbackCacheService != null) {
            ToolCallbackProvider[] toolProviders = mcpToolCallbackCacheService.getToolCallbackProviders();
            if (toolProviders.length > 0) {
                log.debug("Adding {} MCP tool callback providers to chat request", toolProviders.length);
                promptSpec = promptSpec.toolCallbacks(toolProviders);
            }
        } else if (!request.isUseTools()) {
            log.debug("MCP tools disabled by user preference");
        }

        String response = promptSpec.call().content();

        long responseTime = System.currentTimeMillis() - startTime;

        // Save assistant message
        Message savedMessage = conversationService.addMessage(conversationId, Message.MessageRole.ASSISTANT, response, model);

        // Record metrics (estimate tokens based on content length)
        int promptTokens = estimateTokens(request.getMessage());
        int completionTokens = estimateTokens(response);

        // For non-streaming, TTFT equals response time (all tokens arrive at once)
        Long timeToFirstToken = responseTime;

        // Calculate tokens per second
        Double tokensPerSecond = responseTime > 0 ?
                (completionTokens / (responseTime / 1000.0)) : 0.0;

        metricsService.recordUsage(userId, conversationId, model, provider, promptTokens, completionTokens,
                responseTime, timeToFirstToken, tokensPerSecond);

        // Update conversation title if this is the first exchange
        if (conversation.getMessages().size() <= 2) {
            String title = generateTitle(request.getMessage());
            conversationService.updateConversationTitle(conversationId, title);
        }

        return ChatResponse.builder()
                .conversationId(conversationId)
                .messageId(savedMessage.getId())
                .content(response)
                .htmlContent(markdownService.toHtml(response))
                .model(model)
                .complete(true)
                .timeToFirstTokenMs(timeToFirstToken)
                .tokensPerSecond(tokensPerSecond)
                .totalResponseTimeMs(responseTime)
                .build();
    }

    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // Rough estimation: ~4 characters per token on average
        return (int) Math.ceil(text.length() / 4.0);
    }

    public Flux<ChatResponse> chatStream(ChatRequest request) {
        String provider = request.getProvider() != null ? request.getProvider() : chatConfig.getDefaultProvider();
        String model = request.getModel();

        // Get current user ID
        UUID userId = userService.getCurrentUser().map(User::getId).orElse(null);

        // Create or get conversation
        UUID conversationId = request.getConversationId();
        Conversation conversation;

        if (conversationId == null) {
            conversation = conversationService.createConversation(null, provider, model, userId);
            conversationId = conversation.getId();
        } else {
            if (userId != null) {
                conversation = conversationService.getConversationEntityForUser(conversationId, userId)
                        .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
            } else {
                conversation = conversationService.getConversationEntity(conversationId)
                        .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
            }
        }

        final UUID finalConversationId = conversationId;
        final UUID finalUserId = userId;
        final String finalProvider = provider;
        final String userMessage = request.getMessage();

        // Save user message
        conversationService.addMessage(conversationId, Message.MessageRole.USER, request.getMessage(), null);

        // Build prompt with conversation history (with optional skill and document context)
        List<org.springframework.ai.chat.messages.Message> messages = buildMessageHistory(
            conversation, request.getMessage(), request.getSkillId(), userId, request.isUseDocumentContext());
        Prompt prompt = new Prompt(messages);

        // Stream AI response
        StringBuilder fullResponse = new StringBuilder();
        AtomicLong startTime = new AtomicLong(System.currentTimeMillis());
        AtomicLong firstTokenTime = new AtomicLong(0);
        AtomicBoolean firstTokenReceived = new AtomicBoolean(false);
        AtomicInteger tokenCount = new AtomicInteger(0);

        Flux<ChatResponse> responseFlux;

        // Check if we have MCP tools available and user has enabled them
        ToolCallbackProvider[] toolProviders = (request.isUseTools() && mcpToolCallbackCacheService != null)
                ? mcpToolCallbackCacheService.getToolCallbackProviders()
                : new ToolCallbackProvider[0];

        if (!request.isUseTools()) {
            log.debug("MCP tools disabled by user preference for streaming request");
        }

        // If we have tools and user enabled them, use ChatClient for streaming (supports tool callbacks)
        // Otherwise, use ChatModel directly for better compatibility
        if (toolProviders.length > 0) {
            ChatClient chatClient = getChatClient(provider, model);
            if (chatClient == null) {
                return Flux.error(new IllegalStateException("No chat client available for provider: " + provider));
            }

            log.debug("Using ChatClient streaming with {} MCP tool providers", toolProviders.length);

            responseFlux = chatClient.prompt()
                    .messages(messages)
                    .toolCallbacks(toolProviders)
                    .stream()
                    .content()
                    .map(content -> {
                        if (content != null && !content.isEmpty() && firstTokenReceived.compareAndSet(false, true)) {
                            firstTokenTime.set(System.currentTimeMillis());
                        }

                        if (content != null) {
                            fullResponse.append(content);
                            tokenCount.incrementAndGet();
                        }

                        return ChatResponse.builder()
                                .conversationId(finalConversationId)
                                .content(content != null ? content : "")
                                .streaming(true)
                                .complete(false)
                                .build();
                    });
        } else {
            // No tools - use ChatModel for streaming
            ChatModel streamingModel = getStreamingModel(provider, model);

            if (streamingModel == null) {
                return Flux.error(new IllegalStateException("No chat model available for provider: " + provider));
            }

            responseFlux = streamingModel.stream(prompt)
                    .map(chatResponse -> {
                        String content = chatResponse.getResult() != null ?
                                chatResponse.getResult().getOutput().getText() : "";

                        if (content != null && !content.isEmpty() && firstTokenReceived.compareAndSet(false, true)) {
                            firstTokenTime.set(System.currentTimeMillis());
                        }

                        if (content != null) {
                            fullResponse.append(content);
                            tokenCount.incrementAndGet();
                        }

                        return ChatResponse.builder()
                                .conversationId(finalConversationId)
                                .content(content != null ? content : "")
                                .streaming(true)
                                .complete(false)
                                .build();
                    });
        }

        return responseFlux.concatWith(Flux.defer(() -> {
            // Save complete response
            String completeResponse = fullResponse.toString();
            Message savedMessage = conversationService.addMessage(
                    finalConversationId,
                    Message.MessageRole.ASSISTANT,
                    completeResponse,
                    model
            );

            // Calculate metrics
            long endTime = System.currentTimeMillis();
            long responseTime = endTime - startTime.get();
            int promptTokens = estimateTokens(userMessage);
            int completionTokens = estimateTokens(completeResponse);

            // Time to first token
            Long timeToFirstToken = firstTokenTime.get() > 0 ?
                    firstTokenTime.get() - startTime.get() : responseTime;

            // Tokens per second (using estimated completion tokens)
            Double tokensPerSecond = responseTime > 0 ?
                    (completionTokens / (responseTime / 1000.0)) : 0.0;

            log.info("Streaming metrics - TTFT: {}ms, TPS: {}, Total: {}ms, Model: {}",
                    timeToFirstToken, String.format("%.1f", tokensPerSecond), responseTime, model);

            metricsService.recordUsage(finalUserId, finalConversationId, model, finalProvider,
                    promptTokens, completionTokens, responseTime, timeToFirstToken, tokensPerSecond);

            // Update conversation title if first exchange
            Conversation conv = conversationService.getConversationEntity(finalConversationId).orElse(null);
            if (conv != null && conv.getMessages().size() <= 2) {
                String title = generateTitle(userMessage);
                conversationService.updateConversationTitle(finalConversationId, title);
            }

            ChatResponse finalResponse = ChatResponse.builder()
                    .conversationId(finalConversationId)
                    .messageId(savedMessage.getId())
                    .content("")
                    .htmlContent(markdownService.toHtml(completeResponse))
                    .model(model)
                    .streaming(false)
                    .complete(true)
                    .timeToFirstTokenMs(timeToFirstToken)
                    .tokensPerSecond(tokensPerSecond)
                    .totalResponseTimeMs(responseTime)
                    .build();

            log.info("Sending final response with metrics - TTFT: {}, TPS: {}, Total: {}",
                    finalResponse.getTimeToFirstTokenMs(),
                    finalResponse.getTokensPerSecond(),
                    finalResponse.getTotalResponseTimeMs());

            return Flux.just(finalResponse);
        }));
    }

    public List<ModelInfo> getAvailableModels() {
        List<ModelInfo> models = new ArrayList<>();

        // Check if running in cloud profile with GenAI
        boolean isCloudProfile = activeProfile != null && activeProfile.contains("cloud");

        if (isCloudProfile && primaryChatClient != null) {
            // Running on Tanzu with GenAI service - get actual model names
            List<String> genAiModelNames = genAiConfig != null ? genAiConfig.getAvailableModelNames() : List.of();

            if (!genAiModelNames.isEmpty()) {
                for (String modelName : genAiModelNames) {
                    // Skip embedding models - only include chat models
                    if (modelName.toLowerCase().contains("embed")) {
                        continue;
                    }
                    models.add(ModelInfo.builder()
                            .id(modelName)
                            .name(modelName)
                            .provider("genai")
                            .description("Tanzu GenAI: " + modelName)
                            .available(true)
                            .build());
                }
            } else {
                // Fallback if we can't get the model names
                models.add(ModelInfo.builder()
                        .id("genai")
                        .name("Tanzu GenAI")
                        .provider("genai")
                        .description("Model provided by Tanzu GenAI service")
                        .available(true)
                        .build());
            }
        } else {
            // Local development with OpenAI/Ollama
            if (primaryChatClient != null) {
                models.add(ModelInfo.builder()
                        .id("gpt-4o-mini")
                        .name("GPT-4o Mini")
                        .provider("openai")
                        .description("Fast and efficient model for most tasks")
                        .available(true)
                        .build());
                models.add(ModelInfo.builder()
                        .id("gpt-4o")
                        .name("GPT-4o")
                        .provider("openai")
                        .description("Most capable OpenAI model")
                        .available(true)
                        .build());
                models.add(ModelInfo.builder()
                        .id("gpt-4-turbo")
                        .name("GPT-4 Turbo")
                        .provider("openai")
                        .description("Powerful model with vision capabilities")
                        .available(true)
                        .build());
            }
        }

        if (ollamaChatClient != null) {
            models.add(ModelInfo.builder()
                    .id("llama3.2")
                    .name("Llama 3.2")
                    .provider("ollama")
                    .description("Meta's latest open-source model")
                    .available(true)
                    .build());
            models.add(ModelInfo.builder()
                    .id("mistral")
                    .name("Mistral")
                    .provider("ollama")
                    .description("Efficient open-source model")
                    .available(true)
                    .build());
            models.add(ModelInfo.builder()
                    .id("codellama")
                    .name("Code Llama")
                    .provider("ollama")
                    .description("Specialized for code generation")
                    .available(true)
                    .build());
        }

        // Add external binding models
        if (externalBindingService != null) {
            for (String modelName : externalBindingService.getAvailableModelNames()) {
                // Skip embedding models
                if (modelName.toLowerCase().contains("embed")) {
                    continue;
                }
                ExternalBindingService.ExternalModelMetadata metadata =
                        externalBindingService.getModelMetadata().get(modelName);
                String bindingName = metadata != null ? metadata.bindingName() : "External";
                models.add(ModelInfo.builder()
                        .id(modelName)
                        .name(modelName)
                        .provider("external")
                        .description("External API: " + bindingName)
                        .available(true)
                        .build());
            }
        }

        return models;
    }

    private ChatClient getChatClient(String provider) {
        return getChatClient(provider, null);
    }

    private ChatClient getChatClient(String provider, String modelName) {
        if ("ollama".equalsIgnoreCase(provider)) {
            return ollamaChatClient;
        }

        // Check external bindings first if provider is "external" or if model exists in external bindings
        if (externalBindingService != null && modelName != null) {
            if ("external".equalsIgnoreCase(provider) || externalBindingService.hasModel(modelName)) {
                ChatModel externalModel = externalBindingService.getChatModelByName(modelName);
                if (externalModel != null) {
                    log.debug("Using external binding model: {}", modelName);
                    return ChatClient.builder(externalModel).build();
                }
            }
        }

        // For GenAI, try to get the specific model if provided
        if ("genai".equalsIgnoreCase(provider) && modelName != null && genAiConfig != null) {
            ChatClient modelClient = genAiConfig.getChatClientForModel(modelName);
            if (modelClient != null) {
                log.debug("Using GenAI model: {}", modelName);
                return modelClient;
            }
        }

        // Use primary client for openai, genai (default), or any other provider
        return primaryChatClient;
    }

    private ChatModel getStreamingModel(String provider, String modelName) {
        if ("ollama".equalsIgnoreCase(provider)) {
            return ollamaChatModel;
        }

        // Check external bindings first if provider is "external" or if model exists in external bindings
        if (externalBindingService != null && modelName != null) {
            if ("external".equalsIgnoreCase(provider) || externalBindingService.hasModel(modelName)) {
                ChatModel externalModel = externalBindingService.getChatModelByName(modelName);
                if (externalModel != null) {
                    log.debug("Using external binding streaming model: {}", modelName);
                    return externalModel;
                }
            }
        }

        // For GenAI, try to get the specific model if provided
        if ("genai".equalsIgnoreCase(provider) && modelName != null && genAiConfig != null) {
            ChatModel model = genAiConfig.getChatModelByName(modelName);
            if (model != null) {
                log.debug("Using GenAI streaming model: {}", modelName);
                return model;
            }
        }

        // Use primary model for openai, genai (default), or any other provider
        return primaryChatModel;
    }

    private List<org.springframework.ai.chat.messages.Message> buildMessageHistory(
            Conversation conversation, String currentMessage) {
        return buildMessageHistory(conversation, currentMessage, null, null, false);
    }

    private List<org.springframework.ai.chat.messages.Message> buildMessageHistory(
            Conversation conversation, String currentMessage, UUID skillId) {
        return buildMessageHistory(conversation, currentMessage, skillId, null, false);
    }

    private List<org.springframework.ai.chat.messages.Message> buildMessageHistory(
            Conversation conversation, String currentMessage, UUID skillId, UUID userId, boolean useDocumentContext) {
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();

        // Build system prompt (base + optional skill augmentation + optional RAG instructions)
        StringBuilder systemPromptBuilder = new StringBuilder(chatConfig.getSystemPrompt());

        // Add skill augmentation if available
        if (skillId != null && skillService != null) {
            try {
                Skill skill = skillService.getSkillById(skillId).orElse(null);
                if (skill != null && skill.isEnabled() && skill.getSystemPromptAugmentation() != null) {
                    systemPromptBuilder.append("\n\n").append(skill.getSystemPromptAugmentation());
                    log.debug("Applied skill '{}' to system prompt", skill.getName());
                }
            } catch (Exception e) {
                log.warn("Failed to apply skill {}: {}", skillId, e.getMessage());
            }
        }

        // Add document context if user has documents and RAG is enabled
        String documentContext = null;
        if (useDocumentContext && userId != null && documentEmbeddingService != null && documentEmbeddingService.isAvailable()) {
            documentContext = buildDocumentContext(userId, currentMessage);
            if (documentContext != null && !documentContext.isEmpty()) {
                systemPromptBuilder.append("\n\n");
                systemPromptBuilder.append("You have access to the user's uploaded documents. ");
                systemPromptBuilder.append("When answering questions, use the relevant document context provided below. ");
                systemPromptBuilder.append("When citing sources, refer to documents by their filename naturally (e.g., 'According to the manual...' or 'The document states...'). ");
                systemPromptBuilder.append("Do not include internal markers like '--- From:' or section numbers in your response.\n\n");
                systemPromptBuilder.append("DOCUMENT CONTEXT:\n");
                systemPromptBuilder.append("---------------------\n");
                systemPromptBuilder.append(documentContext);
                systemPromptBuilder.append("\n---------------------\n");
                systemPromptBuilder.append("Use the above context to answer the user's question. ");
                systemPromptBuilder.append("If the context doesn't contain relevant information, say so and answer based on your general knowledge.");
            }
        }

        messages.add(new SystemMessage(systemPromptBuilder.toString()));

        // Add conversation history
        for (Message msg : conversation.getMessages()) {
            if (msg.getRole() == Message.MessageRole.USER) {
                messages.add(new UserMessage(msg.getContent()));
            } else if (msg.getRole() == Message.MessageRole.ASSISTANT) {
                messages.add(new AssistantMessage(msg.getContent()));
            }
        }

        // Add current message
        messages.add(new UserMessage(currentMessage));

        return messages;
    }

    /**
     * Build document context by searching user's documents for relevant content.
     * Formats context with source attribution from metadata for the LLM.
     */
    private String buildDocumentContext(UUID userId, String query) {
        if (documentEmbeddingService == null || !documentEmbeddingService.isAvailable()) {
            return null;
        }

        try {
            List<Document> relevantDocs = documentEmbeddingService.searchUserDocuments(userId, query, ragTopK);

            if (relevantDocs.isEmpty()) {
                log.debug("No relevant documents found for user {} and query: {}", userId, query);
                return null;
            }

            StringBuilder contextBuilder = new StringBuilder();
            for (int i = 0; i < relevantDocs.size(); i++) {
                Document doc = relevantDocs.get(i);
                String filename = (String) doc.getMetadata().getOrDefault("filename", "document");
                Object chunkIndex = doc.getMetadata().get("chunk_index");

                // Format context with clear section markers (internal, not for display)
                contextBuilder.append("--- From: ").append(filename);
                if (chunkIndex != null) {
                    contextBuilder.append(" (section ").append(((Number) chunkIndex).intValue() + 1).append(")");
                }
                contextBuilder.append(" ---\n");
                contextBuilder.append(doc.getText());
                contextBuilder.append("\n\n");
            }

            log.debug("Built document context with {} chunks for user {}", relevantDocs.size(), userId);
            return contextBuilder.toString().trim();

        } catch (Exception e) {
            log.warn("Failed to build document context for user {}: {}", userId, e.getMessage());
            return null;
        }
    }

    private String generateTitle(String firstMessage) {
        if (firstMessage == null || firstMessage.isBlank()) {
            return "New Conversation";
        }
        // Take first 50 characters or until first newline
        String title = firstMessage.split("\n")[0];
        if (title.length() > 50) {
            title = title.substring(0, 47) + "...";
        }
        return title;
    }
}
