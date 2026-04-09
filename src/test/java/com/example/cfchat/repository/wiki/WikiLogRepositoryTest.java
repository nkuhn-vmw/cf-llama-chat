package com.example.cfchat.repository.wiki;

import com.example.cfchat.model.wiki.WikiLogEntry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class WikiLogRepositoryTest {

    @Autowired WikiLogRepository repo;

    @Test
    void tailReturnsNewestFirst() {
        UUID user = UUID.randomUUID();
        repo.save(entry(user, "WRITE", "wrote a"));
        repo.save(entry(user, "WRITE", "wrote b"));
        repo.save(entry(user, "LINK",  "linked a->b"));

        List<WikiLogEntry> tail = repo.findByUserIdOrderByTsDesc(user, PageRequest.of(0, 10));
        assertThat(tail).hasSize(3);
        assertThat(tail.get(0).getOp()).isEqualTo("LINK");
    }

    private WikiLogEntry entry(UUID u, String op, String summary) {
        WikiLogEntry e = new WikiLogEntry();
        e.setUserId(u);
        e.setOp(op);
        e.setSummary(summary);
        return e;
    }
}
