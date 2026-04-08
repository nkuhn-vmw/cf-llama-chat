// src/test/java/com/example/cfchat/repository/wiki/WikiPageHistoryRepositoryTest.java
package com.example.cfchat.repository.wiki;

import com.example.cfchat.model.wiki.WikiPageHistory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class WikiPageHistoryRepositoryTest {

    @Autowired WikiPageHistoryRepository repo;

    @Test
    void historyOrderedByVersionDesc() {
        UUID pageId = UUID.randomUUID();
        repo.save(historyRow(pageId, 1, "first"));
        repo.save(historyRow(pageId, 2, "second"));
        repo.save(historyRow(pageId, 3, "third"));

        List<WikiPageHistory> rows = repo.findByPageIdOrderByVersionDesc(pageId);
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).getBodyMd()).isEqualTo("third");
        assertThat(rows.get(2).getBodyMd()).isEqualTo("first");
    }

    private WikiPageHistory historyRow(UUID pageId, int version, String body) {
        WikiPageHistory h = new WikiPageHistory();
        h.setPageId(pageId);
        h.setVersion(version);
        h.setBodyMd(body);
        h.setTitle("t");
        h.setKind("CONCEPT");
        h.setEditedBy("test");
        return h;
    }
}
