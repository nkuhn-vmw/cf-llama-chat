package com.example.cfchat.repository;

import com.example.cfchat.model.ConversationTagLink;
import com.example.cfchat.model.ConversationTagLinkId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ConversationTagLinkRepository extends JpaRepository<ConversationTagLink, ConversationTagLinkId> {
    List<ConversationTagLink> findByConversationId(UUID conversationId);

    @Query("SELECT l.conversationId FROM ConversationTagLink l WHERE l.tagId = :tagId")
    List<UUID> findConversationIdsByTagId(@Param("tagId") Long tagId);

    void deleteByConversationIdAndTagId(UUID conversationId, Long tagId);
}
