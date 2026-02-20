package com.example.cfchat.service;

import com.example.cfchat.dto.ConversationDto;
import com.example.cfchat.model.SharedChat;
import com.example.cfchat.repository.SharedChatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatSharingService {

    private final SharedChatRepository sharedChatRepo;
    private final ConversationService conversationService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public SharedChat shareConversation(UUID conversationId, UUID userId, UUID organizationId) {
        // Check if already shared
        Optional<SharedChat> existing = sharedChatRepo.findByConversationId(conversationId);
        if (existing.isPresent()) {
            return existing.get();
        }

        // Verify ownership
        conversationService.getConversationEntityForUser(conversationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found or access denied"));

        String token = generateToken();
        SharedChat shared = SharedChat.builder()
                .conversationId(conversationId)
                .shareToken(token)
                .createdBy(userId)
                .organizationId(organizationId)
                .build();

        return sharedChatRepo.save(shared);
    }

    @Transactional(readOnly = true)
    public Optional<ConversationDto> getSharedConversation(String token) {
        return sharedChatRepo.findByShareToken(token)
                .filter(sc -> sc.getExpiresAt() == null || sc.getExpiresAt().isAfter(LocalDateTime.now()))
                .flatMap(sc -> conversationService.getConversation(sc.getConversationId()));
    }

    @Transactional
    public void unshareConversation(UUID conversationId, UUID userId) {
        sharedChatRepo.deleteByConversationId(conversationId);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
