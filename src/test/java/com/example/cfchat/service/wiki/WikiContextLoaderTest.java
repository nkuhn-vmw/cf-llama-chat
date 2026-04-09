package com.example.cfchat.service.wiki;

import com.example.cfchat.repository.wiki.WikiPageIndexRow;
import com.example.cfchat.repository.wiki.WikiPageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WikiContextLoaderTest {

    WikiPageRepository repo;
    WikiContextLoader loader;

    @BeforeEach
    void setUp() {
        repo = mock(WikiPageRepository.class);
        loader = new WikiContextLoader(repo, 40, 300);
    }

    @Test
    void emptyWikiReturnsNoDataPreamble() {
        UUID u = UUID.randomUUID();
        when(repo.findTopForIndex(eq(u), any(Pageable.class))).thenReturn(List.of());
        String block = loader.loadIndexBlock(u);
        assertThat(block).contains("persistent wiki");
        assertThat(block).doesNotContain("Your wiki index");
    }

    @Test
    void rendersIndexWithSlugAndTitle() {
        UUID u = UUID.randomUUID();
        when(repo.findTopForIndex(eq(u), any(Pageable.class))).thenReturn(List.of(
            new WikiPageIndexRow(UUID.randomUUID(), "facts/coffee", "Coffee", "FACT"),
            new WikiPageIndexRow(UUID.randomUUID(), "personal/work-style", "Work Style", "PREFERENCE")
        ));
        String block = loader.loadIndexBlock(u);
        assertThat(block).contains("Your wiki index");
        assertThat(block).contains("- [FACT] facts/coffee - Coffee");
        assertThat(block).contains("- [PREFERENCE] personal/work-style - Work Style");
    }

    @Test
    void cacheHitDoesNotQueryRepoTwice() {
        UUID u = UUID.randomUUID();
        when(repo.findTopForIndex(eq(u), any(Pageable.class))).thenReturn(List.of());
        loader.loadIndexBlock(u);
        loader.loadIndexBlock(u);
        verify(repo, times(1)).findTopForIndex(eq(u), any(Pageable.class));
    }

    @Test
    void invalidationForcesReload() {
        UUID u = UUID.randomUUID();
        when(repo.findTopForIndex(eq(u), any(Pageable.class))).thenReturn(List.of());
        loader.loadIndexBlock(u);
        loader.invalidate(u);
        loader.loadIndexBlock(u);
        verify(repo, times(2)).findTopForIndex(eq(u), any(Pageable.class));
    }
}
