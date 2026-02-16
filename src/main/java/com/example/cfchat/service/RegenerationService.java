package com.example.cfchat.service;

import com.example.cfchat.model.Message;
import com.example.cfchat.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegenerationService {

    private final MessageRepository messageRepository;

    /**
     * Prepare for regeneration by deactivating the last assistant message.
     * Returns the conversation ID to trigger a new async chat.
     */
    @Transactional
    public UUID prepareRegeneration(UUID conversationId) {
        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);

        // Find the last active assistant message
        Message lastAssistant = null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message m = messages.get(i);
            if (Boolean.TRUE.equals(m.getActive()) && m.getRole() == Message.MessageRole.ASSISTANT) {
                lastAssistant = m;
                break;
            }
        }

        if (lastAssistant != null) {
            lastAssistant.setActive(false);
            // Store parent message ID for alternatives tracking
            lastAssistant.setParentMessageId(findPrecedingUserMessageId(messages, lastAssistant));
            messageRepository.save(lastAssistant);
            log.info("Deactivated assistant message {} for regeneration", lastAssistant.getId());
        }

        return conversationId;
    }

    /**
     * Get all alternative responses at a given message position.
     */
    @Transactional(readOnly = true)
    public List<Message> getAlternatives(UUID conversationId, UUID messageId) {
        List<Message> allMessages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);

        // Find the target message
        Message target = allMessages.stream()
                .filter(m -> m.getId().equals(messageId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));

        // Find all messages that share the same parent user message
        String parentId = target.getParentMessageId();
        if (parentId == null) {
            // Return just this message if no parent tracking
            return List.of(target);
        }

        return allMessages.stream()
                .filter(m -> m.getRole() == Message.MessageRole.ASSISTANT)
                .filter(m -> parentId.equals(m.getParentMessageId()))
                .collect(Collectors.toList());
    }

    /**
     * Switch the active alternative at a position.
     */
    @Transactional
    public Message switchAlternative(UUID conversationId, UUID messageId) {
        List<Message> alternatives = getAlternatives(conversationId, messageId);

        for (Message alt : alternatives) {
            alt.setActive(alt.getId().equals(messageId));
            messageRepository.save(alt);
        }

        return alternatives.stream()
                .filter(m -> m.getId().equals(messageId))
                .findFirst()
                .orElseThrow();
    }

    private String findPrecedingUserMessageId(List<Message> messages, Message assistantMsg) {
        Message preceding = null;
        for (Message m : messages) {
            if (m.getId().equals(assistantMsg.getId())) break;
            if (Boolean.TRUE.equals(m.getActive()) && m.getRole() == Message.MessageRole.USER) {
                preceding = m;
            }
        }
        return preceding != null ? preceding.getId().toString() : null;
    }
}
