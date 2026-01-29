package com.example.cfchat.service;

import com.example.cfchat.config.ChatConfig;
import com.example.cfchat.dto.ChatRequest;
import com.example.cfchat.dto.ChatResponse;
import com.example.cfchat.model.Conversation;
import com.example.cfchat.model.Message;
import com.example.cfchat.model.ModelInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    public ChatService(
            @Autowired(required = false) ChatClient primaryChatClient,
            @Autowired(required = false) OpenAiChatModel openAiChatModel,
            @Autowired(required = false) @Qualifier("ollamaChatClient") ChatClient ollamaChatClient,
            @Autowired(required = false) OllamaChatModel ollamaChatModel,
            ConversationService conversationService,
            MarkdownService markdownService,
            ChatConfig chatConfig) {
        this.primaryChatClient = primaryChatClient;
        // Use OpenAI model as primary for streaming
        this.primaryChatModel = openAiChatModel;
        this.ollamaChatClient = ollamaChatClient;
        this.ollamaChatModel = ollamaChatModel;
        this.conversationService = conversationService;
        this.markdownService = markdownService;
        this.chatConfig = chatConfig;

        log.info("ChatService initialized - primaryChatClient: {}, ollamaChatClient: {}, primaryChatModel: {}",
                primaryChatClient != null, ollamaChatClient != null,
                openAiChatModel != null ? openAiChatModel.getClass().getSimpleName() : "null");
    }

    public ChatResponse chat(ChatRequest request) {
        String provider = request.getProvider() != null ? request.getProvider() : chatConfig.getDefaultProvider();
        String model = request.getModel();

        // Create or get conversation
        UUID conversationId = request.getConversationId();
        Conversation conversation;

        if (conversationId == null) {
            conversation = conversationService.createConversation(null, provider, model);
            conversationId = conversation.getId();
        } else {
            conversation = conversationService.getConversationEntity(conversationId)
                    .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
        }

        // Save user message
        conversationService.addMessage(conversationId, Message.MessageRole.USER, request.getMessage(), null);

        // Build prompt with conversation history
        List<org.springframework.ai.chat.messages.Message> messages = buildMessageHistory(conversation, request.getMessage());

        // Get AI response
        ChatClient chatClient = getChatClient(provider);
        if (chatClient == null) {
            throw new IllegalStateException("No chat client available for provider: " + provider);
        }

        String response = chatClient.prompt()
                .messages(messages)
                .call()
                .content();

        // Save assistant message
        Message savedMessage = conversationService.addMessage(conversationId, Message.MessageRole.ASSISTANT, response, model);

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
                .build();
    }

    public Flux<ChatResponse> chatStream(ChatRequest request) {
        String provider = request.getProvider() != null ? request.getProvider() : chatConfig.getDefaultProvider();
        String model = request.getModel();

        // Create or get conversation
        UUID conversationId = request.getConversationId();
        Conversation conversation;

        if (conversationId == null) {
            conversation = conversationService.createConversation(null, provider, model);
            conversationId = conversation.getId();
        } else {
            conversation = conversationService.getConversationEntity(conversationId)
                    .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
        }

        final UUID finalConversationId = conversationId;

        // Save user message
        conversationService.addMessage(conversationId, Message.MessageRole.USER, request.getMessage(), null);

        // Build prompt with conversation history
        List<org.springframework.ai.chat.messages.Message> messages = buildMessageHistory(conversation, request.getMessage());
        Prompt prompt = new Prompt(messages);

        // Stream AI response
        StringBuilder fullResponse = new StringBuilder();

        Flux<ChatResponse> responseFlux;

        // Use Ollama for ollama provider, otherwise use primary (OpenAI or GenAI)
        if ("ollama".equalsIgnoreCase(provider) && ollamaChatModel != null) {
            responseFlux = ollamaChatModel.stream(prompt)
                    .map(chatResponse -> {
                        String content = chatResponse.getResult() != null ?
                                chatResponse.getResult().getOutput().getText() : "";
                        if (content != null) {
                            fullResponse.append(content);
                        }
                        return ChatResponse.builder()
                                .conversationId(finalConversationId)
                                .content(content != null ? content : "")
                                .streaming(true)
                                .complete(false)
                                .build();
                    });
        } else if (primaryChatModel != null) {
            responseFlux = primaryChatModel.stream(prompt)
                    .map(chatResponse -> {
                        String content = chatResponse.getResult() != null ?
                                chatResponse.getResult().getOutput().getText() : "";
                        if (content != null) {
                            fullResponse.append(content);
                        }
                        return ChatResponse.builder()
                                .conversationId(finalConversationId)
                                .content(content != null ? content : "")
                                .streaming(true)
                                .complete(false)
                                .build();
                    });
        } else {
            return Flux.error(new IllegalStateException("No chat model available for provider: " + provider));
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

            // Update conversation title if first exchange
            Conversation conv = conversationService.getConversationEntity(finalConversationId).orElse(null);
            if (conv != null && conv.getMessages().size() <= 2) {
                String title = generateTitle(request.getMessage());
                conversationService.updateConversationTitle(finalConversationId, title);
            }

            return Flux.just(ChatResponse.builder()
                    .conversationId(finalConversationId)
                    .messageId(savedMessage.getId())
                    .content("")
                    .htmlContent(markdownService.toHtml(completeResponse))
                    .model(model)
                    .streaming(false)
                    .complete(true)
                    .build());
        }));
    }

    public List<ModelInfo> getAvailableModels() {
        List<ModelInfo> models = new ArrayList<>();

        // Check if running in cloud profile with GenAI
        boolean isCloudProfile = activeProfile != null && activeProfile.contains("cloud");

        if (isCloudProfile && primaryChatClient != null) {
            // Running on Tanzu with GenAI service
            models.add(ModelInfo.builder()
                    .id("genai")
                    .name("Tanzu GenAI")
                    .provider("genai")
                    .description("Model provided by Tanzu GenAI service")
                    .available(true)
                    .build());
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

        return models;
    }

    private ChatClient getChatClient(String provider) {
        if ("ollama".equalsIgnoreCase(provider)) {
            return ollamaChatClient;
        }
        // Use primary client for openai, genai, or any other provider
        return primaryChatClient;
    }

    private List<org.springframework.ai.chat.messages.Message> buildMessageHistory(
            Conversation conversation, String currentMessage) {
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();

        // Add system prompt
        messages.add(new SystemMessage(chatConfig.getSystemPrompt()));

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
