package com.example.cfchat.service.wiki;

import com.example.cfchat.event.WikiOpEvent;
import com.example.cfchat.repository.wiki.WikiPageIndexRow;
import com.example.cfchat.repository.wiki.WikiPageRepository;
import org.springframework.context.event.EventListener;
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
        You have a persistent wiki for this user, scoped only to them.

        WRITING (call wiki_write):
        When the user states something about themselves, their work, their
        preferences, or their decisions, call wiki_write to save it - even if
        it sounds casual. A short preference is still a preference. Examples
        that SHOULD be saved:
        - "i like tacos"  -> PREFERENCE, slug preference/food, "User likes tacos."
        - "I use PostgreSQL with pgvector"  -> FACT, slug facts/database
        - "we decided to ship blue-green"  -> DECISION
        - "my team is called Platform Eng"  -> ENTITY, slug entities/team
        Save preferences and facts proactively without asking permission.
        Use wiki_write when in doubt - the user can always click undo.
        Do NOT save: greetings, weather chitchat, one-off questions, transient
        task state, or anything that won't matter in a future conversation.

        READING (call wiki_search or wiki_read):
        Before answering questions about the user, their projects, preferences,
        or prior decisions, search the wiki first. Don't ask "what do you
        prefer?" if the answer is already saved.

        The index below shows what's already in this user's wiki. If a relevant
        slug is listed, prefer wiki_read over re-asking the user.
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

    /**
     * Drop the cached index block whenever the agent (or a user) mutates the
     * wiki, so the next chat turn sees its own writes immediately instead of
     * waiting up to 5 minutes for the TTL. Without this hook, the model can
     * write a fact and then in the very next turn answer "I don't see that
     * in your wiki" because the cached index it's reading from is stale.
     */
    @EventListener
    public void onWikiOp(WikiOpEvent evt) {
        if (evt.getUserId() != null) {
            cache.invalidate(evt.getUserId());
        }
    }

    private String build(UUID userId) {
        List<WikiPageIndexRow> rows = repo.findTopForIndex(userId, Pageable.ofSize(maxEntries));
        if (rows.isEmpty()) return PREAMBLE.trim();
        String body = rows.stream()
            .map(r -> "- [" + r.kind() + "] " + r.slug() + " - " + r.title())
            .collect(Collectors.joining("\n"));
        return PREAMBLE.trim() + "\n\n## Your wiki index\n" + body;
    }
}
