package com.example.cfchat.repository;

import com.example.cfchat.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    List<Conversation> findAllByOrderByUpdatedAtDesc();

    List<Conversation> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    @Query("SELECT c FROM Conversation c LEFT JOIN FETCH c.messages WHERE c.id = :id")
    Optional<Conversation> findByIdWithMessages(UUID id);

    @Query("SELECT c FROM Conversation c LEFT JOIN FETCH c.messages WHERE c.id = :id AND c.userId = :userId")
    Optional<Conversation> findByIdAndUserIdWithMessages(UUID id, UUID userId);

    @Query("SELECT c FROM Conversation c WHERE c.title LIKE %:keyword% ORDER BY c.updatedAt DESC")
    List<Conversation> searchByTitle(String keyword);

    @Query("SELECT c FROM Conversation c WHERE c.userId = :userId AND c.title LIKE %:keyword% ORDER BY c.updatedAt DESC")
    List<Conversation> searchByTitleAndUserId(String keyword, UUID userId);

    long countByUserId(UUID userId);

    void deleteByUserId(UUID userId);
}
