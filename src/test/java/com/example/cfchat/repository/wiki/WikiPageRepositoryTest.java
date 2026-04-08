package com.example.cfchat.repository.wiki;

import com.example.cfchat.model.wiki.WikiPage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class WikiPageRepositoryTest {

    @Autowired WikiPageRepository repo;

    @Test
    void saveAndFindBySlug() {
        UUID userId = UUID.randomUUID();
        WikiPage p = new WikiPage();
        p.setUserId(userId);
        p.setSlug("personal/work-style");
        p.setTitle("Work Style");
        p.setKind("PREFERENCE");
        p.setOrigin("AGENT_WRITE");
        p.setBodyMd("prefers async communication");
        p.setEmbeddingStatus("PENDING");

        repo.save(p);

        Optional<WikiPage> found = repo.findByUserIdAndSlug(userId, "personal/work-style");
        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Work Style");
        assertThat(found.get().getCreatedAt()).isNotNull();
        assertThat(found.get().getUpdatedAt()).isNotNull();
    }

    @Test
    void userScopeIsolation() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        repo.save(pageFor(a, "facts/x"));
        repo.save(pageFor(b, "facts/x"));  // same slug, different user — allowed

        assertThat(repo.findByUserIdAndSlug(a, "facts/x")).isPresent();
        assertThat(repo.findByUserIdAndSlug(b, "facts/x")).isPresent();
        assertThat(repo.countByUserId(a)).isEqualTo(1);
    }

    @Test
    void findTopForIndexOrdersByLastReadThenUpdated() {
        UUID u = UUID.randomUUID();
        WikiPage older = pageFor(u, "concepts/a");
        older.setLastReadAt(Instant.now().minusSeconds(3600));
        WikiPage newer = pageFor(u, "concepts/b");
        newer.setLastReadAt(Instant.now());
        WikiPage neverRead = pageFor(u, "concepts/c");
        repo.save(older);
        repo.save(newer);
        repo.save(neverRead);

        List<WikiPageIndexRow> rows = repo.findTopForIndex(u, 10);
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).slug()).isEqualTo("concepts/b");  // most recent read
        assertThat(rows.get(1).slug()).isEqualTo("concepts/a");
        assertThat(rows.get(2).slug()).isEqualTo("concepts/c");  // NULLS LAST
    }

    private WikiPage pageFor(UUID userId, String slug) {
        WikiPage p = new WikiPage();
        p.setUserId(userId);
        p.setSlug(slug);
        p.setTitle(slug);
        p.setKind("CONCEPT");
        p.setOrigin("AGENT_WRITE");
        p.setBodyMd("body");
        p.setEmbeddingStatus("PENDING");
        return p;
    }
}
