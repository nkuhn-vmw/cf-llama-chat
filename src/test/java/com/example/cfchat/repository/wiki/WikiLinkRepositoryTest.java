// src/test/java/com/example/cfchat/repository/wiki/WikiLinkRepositoryTest.java
package com.example.cfchat.repository.wiki;

import com.example.cfchat.model.wiki.WikiLink;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class WikiLinkRepositoryTest {

    @Autowired WikiLinkRepository repo;

    @Test
    void findByFromOrTo() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        repo.save(link(a, b, "see_also"));
        repo.save(link(b, c, "refines"));

        assertThat(repo.findByFromPageId(a)).hasSize(1);
        assertThat(repo.findByToPageId(b)).hasSize(1);
        assertThat(repo.findByFromPageIdOrToPageId(b, b)).hasSize(2);
    }

    private WikiLink link(UUID from, UUID to, String relation) {
        WikiLink l = new WikiLink();
        l.setFromPageId(from);
        l.setToPageId(to);
        l.setRelation(relation);
        l.setCreatedBy("test");
        return l;
    }
}
