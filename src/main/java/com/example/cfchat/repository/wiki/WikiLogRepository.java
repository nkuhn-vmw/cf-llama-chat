package com.example.cfchat.repository.wiki;

import com.example.cfchat.model.wiki.WikiLogEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WikiLogRepository extends JpaRepository<WikiLogEntry, UUID> {

    List<WikiLogEntry> findByUserIdOrderByTsDesc(UUID userId, Pageable pageable);
}
