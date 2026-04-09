package com.example.cfchat.repository.wiki;

import com.example.cfchat.model.wiki.WikiPageHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WikiPageHistoryRepository extends JpaRepository<WikiPageHistory, UUID> {

    List<WikiPageHistory> findByPageIdOrderByVersionDesc(UUID pageId);

    Optional<WikiPageHistory> findFirstByPageIdOrderByVersionDesc(UUID pageId);
}
