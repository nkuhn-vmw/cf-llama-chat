package com.example.cfchat.repository;

import com.example.cfchat.model.ConversationTag;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ConversationTagRepository extends JpaRepository<ConversationTag, Long> {
    List<ConversationTag> findByUserId(UUID userId);
}
