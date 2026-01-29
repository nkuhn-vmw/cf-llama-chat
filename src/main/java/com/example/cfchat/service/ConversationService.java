package com.example.cfchat.service;

import com.example.cfchat.dto.ConversationDto;
import com.example.cfchat.model.Conversation;
import com.example.cfchat.model.Message;
import com.example.cfchat.repository.ConversationRepository;
import com.example.cfchat.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    @Transactional
    public Conversation createConversation(String title, String provider, String model) {
        Conversation conversation = Conversation.builder()
                .title(title != null ? title : "New Conversation")
                .modelProvider(provider)
                .modelName(model)
                .build();
        return conversationRepository.save(conversation);
    }

    @Transactional(readOnly = true)
    public List<ConversationDto> getAllConversations() {
        return conversationRepository.findAllByOrderByUpdatedAtDesc()
                .stream()
                .map(c -> ConversationDto.fromEntity(c, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ConversationDto> getConversation(UUID id) {
        return conversationRepository.findByIdWithMessages(id)
                .map(c -> ConversationDto.fromEntity(c, true));
    }

    @Transactional(readOnly = true)
    public Optional<Conversation> getConversationEntity(UUID id) {
        return conversationRepository.findByIdWithMessages(id);
    }

    @Transactional
    public Message addMessage(UUID conversationId, Message.MessageRole role, String content, String model) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));

        Message message = Message.builder()
                .role(role)
                .content(content)
                .modelUsed(model)
                .build();

        conversation.addMessage(message);
        conversationRepository.save(conversation);

        return message;
    }

    @Transactional
    public void updateConversationTitle(UUID id, String title) {
        conversationRepository.findById(id).ifPresent(c -> {
            c.setTitle(title);
            conversationRepository.save(c);
        });
    }

    @Transactional
    public void deleteConversation(UUID id) {
        conversationRepository.deleteById(id);
        log.info("Deleted conversation: {}", id);
    }

    @Transactional(readOnly = true)
    public List<Message> getMessages(UUID conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }
}
