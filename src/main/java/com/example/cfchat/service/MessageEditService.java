package com.example.cfchat.service;

import com.example.cfchat.model.Message;
import com.example.cfchat.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageEditService {

    private final MessageRepository messageRepository;
    private final ConversationService conversationService;

    @Transactional
    public Message editMessage(UUID conversationId, UUID messageId, String newContent, boolean regenerate) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));

        if (message.getRole() != Message.MessageRole.USER) {
            throw new IllegalArgumentException("Only user messages can be edited");
        }

        if (!message.getConversation().getId().equals(conversationId)) {
            throw new IllegalArgumentException("Message does not belong to this conversation");
        }

        // Update the message content
        message.setContent(newContent);
        messageRepository.save(message);

        if (regenerate) {
            // Deactivate all messages after this one
            List<Message> allMessages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
            boolean foundEdited = false;
            for (Message m : allMessages) {
                if (m.getId().equals(messageId)) {
                    foundEdited = true;
                    continue;
                }
                if (foundEdited) {
                    m.setActive(false);
                    messageRepository.save(m);
                }
            }
            log.info("Edited message {} and deactivated subsequent messages for regeneration", messageId);
        }

        return message;
    }
}
