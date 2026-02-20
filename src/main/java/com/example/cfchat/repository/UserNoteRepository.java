package com.example.cfchat.repository;

import com.example.cfchat.model.UserNote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface UserNoteRepository extends JpaRepository<UserNote, UUID> {
    List<UserNote> findByUserIdOrderByUpdatedAtDesc(UUID userId);
    List<UserNote> findByConversationId(UUID conversationId);
}
