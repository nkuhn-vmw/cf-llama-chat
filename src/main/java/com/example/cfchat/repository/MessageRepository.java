package com.example.cfchat.repository;

import com.example.cfchat.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

    List<Message> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    List<Message> findByConversationIdAndActiveTrueOrderByCreatedAtAsc(UUID conversationId);

    long countByConversationId(UUID conversationId);
}
