package com.example.cfchat.service.wiki;

import com.example.cfchat.model.wiki.WikiPage;
import com.example.cfchat.repository.wiki.WikiPageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Component
public class WikiMigrationRunner {

    private static final Logger log = LoggerFactory.getLogger(WikiMigrationRunner.class);
    private static final String FLAG = "wiki_v1";

    private final JdbcTemplate jdbc;
    private final WikiPageRepository pageRepo;

    public WikiMigrationRunner(JdbcTemplate jdbc, WikiPageRepository pageRepo) {
        this.jdbc = jdbc;
        this.pageRepo = pageRepo;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrate() {
        ensureFlagTable();
        if (alreadyRan()) {
            log.info("Wiki migration already ran, skipping");
            return;
        }
        log.info("Starting wiki migration (notes + memories -> wiki_page)");
        int notes = migrateNotes();
        int memories = migrateMemories();
        dropOldTables();
        markDone();
        log.info("Wiki migration complete: {} notes, {} memories -> wiki_page", notes, memories);
    }

    private void ensureFlagTable() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS app_migration (
              id VARCHAR(64) PRIMARY KEY,
              completed_at TIMESTAMP NOT NULL
            )
        """);
    }

    private boolean alreadyRan() {
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_migration WHERE id = ?", Integer.class, FLAG);
        return c != null && c > 0;
    }

    private void markDone() {
        jdbc.update(
                "INSERT INTO app_migration (id, completed_at) VALUES (?, CURRENT_TIMESTAMP)", FLAG);
    }

    private int migrateNotes() {
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList("SELECT id, user_id, title, content, conversation_id FROM user_notes");
        } catch (Exception e) {
            log.info("user_notes table not present; skipping notes migration");
            return 0;
        }

        Set<String> seenSlugs = new HashSet<>();
        int count = 0;
        for (Map<String, Object> row : rows) {
            UUID userId = toUuid(row.get("user_id"));
            String title = (String) row.get("title");
            String content = (String) row.get("content");
            UUID convId = toUuid(row.get("conversation_id"));
            if (content == null) continue;

            String baseSlug = "notes/" + SlugUtil.slugify(title == null ? "untitled-" + row.get("id") : title);
            String slug = uniqueSlug(userId, baseSlug, seenSlugs);

            WikiPage p = new WikiPage();
            p.setUserId(userId);
            p.setSlug(slug);
            p.setTitle(title == null || title.isBlank() ? "Untitled Note" : title);
            p.setKind("CONCEPT");
            p.setOrigin("MIGRATED_NOTE");
            p.setBodyMd(content);
            p.setSourceConversationId(convId);
            p.setEmbeddingStatus("PENDING");
            pageRepo.save(p);
            count++;
        }
        return count;
    }

    private int migrateMemories() {
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList("SELECT id, user_id, content, category FROM user_memories");
        } catch (Exception e) {
            log.info("user_memories table not present; skipping memories migration");
            return 0;
        }

        Set<String> seenSlugs = new HashSet<>();
        int count = 0;
        for (Map<String, Object> row : rows) {
            UUID userId = toUuid(row.get("user_id"));
            String content = (String) row.get("content");
            String category = (String) row.get("category");
            if (content == null) continue;

            String kind;
            String prefix;
            switch (category == null ? "general" : category.toLowerCase()) {
                case "fact" -> { kind = "FACT"; prefix = "facts"; }
                case "preference" -> { kind = "PREFERENCE"; prefix = "preferences"; }
                case "instruction" -> { kind = "CONCEPT"; prefix = "instructions"; }
                default -> { kind = "CONCEPT"; prefix = "general"; }
            }

            String baseSlug = prefix + "/" + SlugUtil.hash8(content);
            String slug = uniqueSlug(userId, baseSlug, seenSlugs);
            String title = content.length() <= 60 ? content : content.substring(0, 57) + "...";

            WikiPage p = new WikiPage();
            p.setUserId(userId);
            p.setSlug(slug);
            p.setTitle(title);
            p.setKind(kind);
            p.setOrigin("MIGRATED_MEMORY");
            p.setBodyMd(content);
            p.setEmbeddingStatus("PENDING");
            pageRepo.save(p);
            count++;
        }
        return count;
    }

    private String uniqueSlug(UUID userId, String base, Set<String> sessionSeen) {
        String slug = base;
        int suffix = 2;
        while (sessionSeen.contains(userId + "|" + slug)
                || pageRepo.findByUserIdAndSlug(userId, slug).isPresent()) {
            slug = base + "-" + suffix++;
        }
        sessionSeen.add(userId + "|" + slug);
        return slug;
    }

    private void dropOldTables() {
        try {
            jdbc.execute("DROP TABLE IF EXISTS user_notes");
            jdbc.execute("DROP TABLE IF EXISTS user_memories");
        } catch (Exception e) {
            log.warn("Could not drop old tables (they may not exist): {}", e.getMessage());
        }
    }

    private static UUID toUuid(Object v) {
        if (v == null) return null;
        if (v instanceof UUID u) return u;
        return UUID.fromString(v.toString());
    }
}
