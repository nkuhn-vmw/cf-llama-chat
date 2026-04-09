// src/main/java/com/example/cfchat/repository/wiki/WikiLinkRepository.java
package com.example.cfchat.repository.wiki;

import com.example.cfchat.model.wiki.WikiLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WikiLinkRepository extends JpaRepository<WikiLink, UUID> {

    List<WikiLink> findByFromPageId(UUID fromPageId);
    List<WikiLink> findByToPageId(UUID toPageId);
    List<WikiLink> findByFromPageIdOrToPageId(UUID a, UUID b);

    Optional<WikiLink> findByFromPageIdAndToPageIdAndRelation(UUID from, UUID to, String relation);

    void deleteByFromPageIdOrToPageId(UUID a, UUID b);
}
