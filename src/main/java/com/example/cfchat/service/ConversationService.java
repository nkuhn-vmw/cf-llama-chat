package com.example.cfchat.service;

import com.example.cfchat.dto.ConversationDto;
import com.example.cfchat.model.Conversation;
import com.example.cfchat.model.Message;
import com.example.cfchat.repository.ConversationRepository;
import com.example.cfchat.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
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
    public Conversation createConversation(String title, String provider, String model, UUID userId) {
        Conversation conversation = Conversation.builder()
                .title(title != null ? title : "New Conversation")
                .modelProvider(provider)
                .modelName(model)
                .userId(userId)
                .build();
        return conversationRepository.save(conversation);
    }

    @Transactional
    public Conversation createConversation(String title, String provider, String model) {
        return createConversation(title, provider, model, null);
    }

    @Transactional(readOnly = true)
    public List<ConversationDto> getAllConversations() {
        return conversationRepository.findAllByOrderByUpdatedAtDesc()
                .stream()
                .map(c -> ConversationDto.fromEntity(c, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ConversationDto> getConversationsForUser(UUID userId) {
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId, PageRequest.of(0, 100))
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
    public Optional<ConversationDto> getConversationForUser(UUID id, UUID userId) {
        return conversationRepository.findByIdAndUserIdWithMessages(id, userId)
                .map(c -> ConversationDto.fromEntity(c, true));
    }

    @Transactional(readOnly = true)
    public Optional<Conversation> getConversationEntity(UUID id) {
        return conversationRepository.findByIdWithMessages(id);
    }

    @Transactional(readOnly = true)
    public Optional<Conversation> getConversationEntityForUser(UUID id, UUID userId) {
        return conversationRepository.findByIdAndUserIdWithMessages(id, userId);
    }

    @Transactional(readOnly = true)
    public long getConversationCountForUser(UUID userId) {
        return conversationRepository.countByUserId(userId);
    }

    @Transactional(readOnly = true)
    public boolean isOwnedByUser(UUID conversationId, UUID userId) {
        return conversationRepository.findById(conversationId)
                .map(c -> userId.equals(c.getUserId()))
                .orElse(false);
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

    @Transactional
    public int deleteAllConversationsForUser(UUID userId) {
        List<Conversation> conversations = conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        int count = conversations.size();
        conversationRepository.deleteByUserId(userId);
        log.info("Deleted {} conversations for user: {}", count, userId);
        return count;
    }

    @Transactional(readOnly = true)
    public List<Message> getMessages(UUID conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }
}
