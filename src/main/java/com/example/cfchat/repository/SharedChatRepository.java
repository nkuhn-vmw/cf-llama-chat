package com.example.cfchat.repository;

import com.example.cfchat.model.SharedChat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SharedChatRepository extends JpaRepository<SharedChat, UUID> {
    Optional<SharedChat> findByShareToken(String shareToken);
    Optional<SharedChat> findByConversationId(UUID conversationId);
    void deleteByConversationId(UUID conversationId);
}
