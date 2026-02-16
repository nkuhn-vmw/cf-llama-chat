package com.example.cfchat.service;

import com.example.cfchat.model.Conversation;
import com.example.cfchat.model.Message;
import com.example.cfchat.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncChatService {

    private final ConversationService conversationService;
    private final SseEmitterRegistry emitterRegistry;
    private final MessageRepository messageRepository;

    /**
     * Process a chat request asynchronously. The response is persisted even if
     * the SSE connection is lost (user navigates away).
     */
    @Async("chatExecutor")
    public void processChat(UUID conversationId, String modelId, ChatModel chatModel) {
        try {
            Conversation conv = conversationService.getConversationEntity(conversationId)
                    .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));

            List<org.springframework.ai.chat.messages.Message> history = buildHistory(conv);

            StringBuilder accumulated = new StringBuilder();
            long startTime = System.currentTimeMillis();

            chatModel.stream(new Prompt(history))
                    .doOnNext(response -> {
                        String chunk = response.getResult() != null
                                ? response.getResult().getOutput().getText() : "";
                        if (chunk != null && !chunk.isEmpty()) {
                            accumulated.append(chunk);
                            emitterRegistry.trySend(conversationId, chunk);
                        }
                    })
                    .doOnComplete(() -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        Message msg = conversationService.addMessage(
                                conversationId, Message.MessageRole.ASSISTANT,
                                accumulated.toString(), modelId);
                        msg.setGenerationTimeMs(elapsed);
                        msg.setTokenCount(estimateTokens(accumulated.toString()));
                        messageRepository.save(msg);

                        // Signal completion via SSE
                        emitterRegistry.trySendEvent(conversationId, "complete",
                                "{\"messageId\":\"" + msg.getId() + "\",\"generationTimeMs\":" + elapsed + "}");
                        emitterRegistry.complete(conversationId);

                        log.info("Async chat completed for conversation {} in {}ms", conversationId, elapsed);
                    })
                    .doOnError(err -> {
                        log.error("Async chat error for conversation {}: {}", conversationId, err.getMessage());
                        conversationService.addMessage(
                                conversationId, Message.MessageRole.ASSISTANT,
                                "[Error: " + err.getMessage() + "]", modelId);
                        emitterRegistry.trySendEvent(conversationId, "error",
                                "{\"error\":\"" + err.getMessage().replace("\"", "'") + "\"}");
                        emitterRegistry.complete(conversationId);
                    })
                    .subscribe();

        } catch (Exception e) {
            log.error("Failed to start async chat for conversation {}: {}", conversationId, e.getMessage());
            emitterRegistry.complete(conversationId);
        }
    }

    private List<org.springframework.ai.chat.messages.Message> buildHistory(Conversation conv) {
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();

        if (conv.getMessages() != null) {
            for (Message msg : conv.getMessages()) {
                // Only include active messages
                if (!Boolean.TRUE.equals(msg.getActive())) continue;

                switch (msg.getRole()) {
                    case SYSTEM -> messages.add(new SystemMessage(msg.getContent()));
                    case USER -> messages.add(new UserMessage(msg.getContent()));
                    case ASSISTANT -> messages.add(new AssistantMessage(msg.getContent()));
                }
            }
        }

        return messages;
    }

    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return (int) Math.ceil(text.length() / 4.0);
    }
}
