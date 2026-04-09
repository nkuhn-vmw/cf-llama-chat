// src/test/java/com/example/cfchat/service/wiki/WikiServiceTest.java
package com.example.cfchat.service.wiki;

import com.example.cfchat.dto.wiki.WikiPageView;
import com.example.cfchat.event.WikiOpEvent;
import com.example.cfchat.model.wiki.WikiPage;
import com.example.cfchat.model.wiki.WikiPageHistory;
import com.example.cfchat.model.wiki.WikiLogEntry;
import com.example.cfchat.repository.wiki.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WikiServiceTest {

    WikiPageRepository pageRepo;
    WikiPageHistoryRepository historyRepo;
    WikiLinkRepository linkRepo;
    WikiLogRepository logRepo;
    WikiEmbeddingService embeddingService;
    ApplicationEventPublisher publisher;
    WikiContextLoader contextLoader;

    WikiService service;

    @BeforeEach
    void setUp() {
        pageRepo = mock(WikiPageRepository.class);
        historyRepo = mock(WikiPageHistoryRepository.class);
        linkRepo = mock(WikiLinkRepository.class);
        logRepo = mock(WikiLogRepository.class);
        embeddingService = mock(WikiEmbeddingService.class);
        publisher = mock(ApplicationEventPublisher.class);
        contextLoader = mock(WikiContextLoader.class);
        service = new WikiService(pageRepo, historyRepo, linkRepo, logRepo,
                                  embeddingService, publisher, contextLoader);

        when(pageRepo.save(any(WikiPage.class))).thenAnswer(inv -> {
            WikiPage p = inv.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return p;
        });
    }

    @Test
    void upsertCreatesNewPageAndEmitsEvent() {
        UUID user = UUID.randomUUID();
        UUID conv = UUID.randomUUID();
        when(pageRepo.findByUserIdAndSlug(user, "personal/work-style"))
            .thenReturn(Optional.empty());

        WikiPageView view = service.upsert(
            new WikiScope(user, conv),
            "personal/work-style", "Work Style", "PREFERENCE",
            "prefers async", "AGENT_WRITE");

        assertThat(view.title()).isEqualTo("Work Style");
        verify(pageRepo, atLeastOnce()).save(any(WikiPage.class));
        verify(historyRepo, never()).save(any());   // new page — no prior history
        verify(logRepo).save(any(WikiLogEntry.class));
        verify(embeddingService).indexPage(any(WikiPage.class));
        verify(contextLoader).invalidate(user);

        ArgumentCaptor<WikiOpEvent> evt = ArgumentCaptor.forClass(WikiOpEvent.class);
        verify(publisher).publishEvent(evt.capture());
        assertThat(evt.getValue().getPayload().op()).isEqualTo("WRITE");
        assertThat(evt.getValue().getUserId()).isEqualTo(user);
    }

    @Test
    void upsertExistingPageSnapshotsHistoryFirst() {
        UUID user = UUID.randomUUID();
        WikiPage existing = new WikiPage();
        existing.setId(UUID.randomUUID());
        existing.setUserId(user);
        existing.setSlug("facts/x");
        existing.setTitle("old title");
        existing.setKind("FACT");
        existing.setOrigin("AGENT_WRITE");
        existing.setBodyMd("old body");
        existing.setVersion(3);
        existing.setEmbeddingStatus("READY");

        when(pageRepo.findByUserIdAndSlug(user, "facts/x"))
            .thenReturn(Optional.of(existing));

        service.upsert(new WikiScope(user, null),
            "facts/x", "new title", "FACT", "new body", "AGENT_WRITE");

        ArgumentCaptor<WikiPageHistory> historyCap = ArgumentCaptor.forClass(WikiPageHistory.class);
        verify(historyRepo).save(historyCap.capture());
        assertThat(historyCap.getValue().getBodyMd()).isEqualTo("old body");
        assertThat(historyCap.getValue().getVersion()).isEqualTo(3);
        assertThat(existing.getTitle()).isEqualTo("new title");
        assertThat(existing.getBodyMd()).isEqualTo("new body");
    }

    @Test
    void invalidateRemovesEmbeddingsAndMarksFrontmatter() {
        UUID user = UUID.randomUUID();
        WikiPage existing = new WikiPage();
        existing.setId(UUID.randomUUID());
        existing.setUserId(user);
        existing.setSlug("facts/x");
        existing.setTitle("x");
        existing.setKind("FACT");
        existing.setOrigin("AGENT_WRITE");
        existing.setBodyMd("body");
        existing.setEmbeddingStatus("READY");
        when(pageRepo.findByUserIdAndSlug(user, "facts/x")).thenReturn(Optional.of(existing));

        service.invalidate(new WikiScope(user, null), "facts/x", "superseded");

        verify(embeddingService).deletePageEmbeddings(existing.getId());
        assertThat(existing.getFrontmatter()).containsKey("invalidated_at");
        assertThat(existing.getFrontmatter().get("invalidated_reason")).isEqualTo("superseded");
        verify(historyRepo).save(any(WikiPageHistory.class));
        verify(logRepo).save(any(WikiLogEntry.class));
        verify(publisher).publishEvent(any(WikiOpEvent.class));
    }

    @Test
    void undoRestoresMostRecentHistoryRow() {
        UUID user = UUID.randomUUID();
        UUID pageId = UUID.randomUUID();
        WikiPage current = new WikiPage();
        current.setId(pageId);
        current.setUserId(user);
        current.setSlug("facts/x");
        current.setTitle("current title");
        current.setKind("FACT");
        current.setOrigin("AGENT_WRITE");
        current.setBodyMd("current body");
        current.setVersion(5);
        current.setEmbeddingStatus("READY");

        WikiPageHistory prior = new WikiPageHistory();
        prior.setPageId(pageId);
        prior.setVersion(4);
        prior.setTitle("prior title");
        prior.setBodyMd("prior body");
        prior.setKind("FACT");
        prior.setEditedBy("agent:conv");

        when(pageRepo.findById(pageId)).thenReturn(Optional.of(current));
        when(historyRepo.findFirstByPageIdOrderByVersionDesc(pageId)).thenReturn(Optional.of(prior));

        service.undo(new WikiScope(user, null), pageId);

        assertThat(current.getTitle()).isEqualTo("prior title");
        assertThat(current.getBodyMd()).isEqualTo("prior body");
        verify(embeddingService).indexPage(current);
        verify(logRepo).save(any(WikiLogEntry.class));
        verify(publisher).publishEvent(any(WikiOpEvent.class));
    }

    @Test
    void undoRejectsPageOwnedByDifferentUser() {
        UUID user = UUID.randomUUID();
        UUID otherUser = UUID.randomUUID();
        UUID pageId = UUID.randomUUID();
        WikiPage page = new WikiPage();
        page.setId(pageId);
        page.setUserId(otherUser);
        when(pageRepo.findById(pageId)).thenReturn(Optional.of(page));

        assertThatThrownBy(() -> service.undo(new WikiScope(user, null), pageId))
            .isInstanceOf(SecurityException.class);
    }
}
