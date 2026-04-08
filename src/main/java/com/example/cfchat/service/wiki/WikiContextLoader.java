package com.example.cfchat.service.wiki;

import com.example.cfchat.repository.wiki.WikiPageIndexRow;
import com.example.cfchat.repository.wiki.WikiPageRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class WikiContextLoader {

    private static final String PREAMBLE = """
        You have a persistent wiki for this user. Call wiki_search or wiki_read
        before answering questions about them, their projects, preferences, or
        prior decisions. Call wiki_write to persist durable new facts they share.
        The wiki is scoped to this user - you cannot access other users' data.
        """;

    private final WikiPageRepository repo;
    private final int maxEntries;
    private final Cache<UUID, String> cache;

    public WikiContextLoader(
            WikiPageRepository repo,
            @Value("${app.wiki.index.max-entries:40}") int maxEntries,
            @Value("${app.wiki.index.cache-ttl-seconds:300}") int ttlSeconds) {
        this.repo = repo;
        this.maxEntries = maxEntries;
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
            .maximumSize(10_000)
            .build();
    }

    public String loadIndexBlock(UUID userId) {
        return cache.get(userId, this::build);
    }

    public void invalidate(UUID userId) { cache.invalidate(userId); }

    private String build(UUID userId) {
        List<WikiPageIndexRow> rows = repo.findTopForIndex(userId, Pageable.ofSize(maxEntries));
        if (rows.isEmpty()) return PREAMBLE.trim();
        String body = rows.stream()
            .map(r -> "- [" + r.kind() + "] " + r.slug() + " - " + r.title())
            .collect(Collectors.joining("\n"));
        return PREAMBLE.trim() + "\n\n## Your wiki index\n" + body;
    }
}
