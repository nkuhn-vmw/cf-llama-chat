package com.example.cfchat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "rag.query-rewrite.enabled", havingValue = "true", matchIfMissing = true)
public class QueryRewriteService {
    private final ChatModel chatModel;
    @Value("${rag.query-rewrite.enabled:true}") private boolean enabled;

    public record ChatMessage(String role, String content) {}

    public String rewrite(String userMsg, List<ChatMessage> history) {
        if (!enabled || history.size() < 2) return userMsg;
        String recent = history.stream()
            .skip(Math.max(0, history.size() - 6))
            .map(m -> m.role() + ": " + m.content().substring(0, Math.min(m.content().length(), 200)))
            .collect(Collectors.joining("\n"));
        String prompt = "Given this conversation and latest message, generate a standalone search query:\n" + recent + "\nLatest: " + userMsg + "\nReply with ONLY the query.";
        try {
            return chatModel.call(new Prompt(prompt)).getResult().getOutput().getText().trim();
        } catch (Exception e) {
            log.warn("Query rewrite failed, using original: {}", e.getMessage());
            return userMsg;
        }
    }
}
