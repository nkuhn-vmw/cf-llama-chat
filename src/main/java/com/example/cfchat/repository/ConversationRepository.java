package com.example.cfchat.repository;

import com.example.cfchat.model.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    List<Conversation> findAllByOrderByUpdatedAtDesc();

    List<Conversation> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    List<Conversation> findByUserIdOrderByUpdatedAtDesc(UUID userId, Pageable pageable);

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

    @Query("SELECT c FROM Conversation c WHERE c.userId = :uid AND (c.archived = false OR c.archived IS NULL) ORDER BY c.updatedAt DESC")
    Page<Conversation> findActiveByUserId(@Param("uid") UUID uid, Pageable p);

    @Query("SELECT c FROM Conversation c WHERE c.userId = :uid AND c.archived = true ORDER BY c.updatedAt DESC")
    Page<Conversation> findArchivedByUserId(@Param("uid") UUID uid, Pageable p);

    @Query("SELECT c FROM Conversation c WHERE c.userId = :userId AND (c.pinned = true) ORDER BY c.updatedAt DESC")
    List<Conversation> findByUserIdAndPinnedTrueOrderByUpdatedAtDesc(@Param("userId") UUID userId);

    @Query("SELECT c FROM Conversation c WHERE c.userId = :uid AND LOWER(c.title) LIKE LOWER(CONCAT('%',:q,'%'))")
    Page<Conversation> searchByTitle(@Param("uid") UUID uid, @Param("q") String q, Pageable p);

    @Modifying
    @Query("UPDATE Conversation c SET c.archived = true WHERE c.userId = :uid AND (c.archived = false OR c.archived IS NULL)")
    int archiveAllByUserId(@Param("uid") UUID uid);
}
