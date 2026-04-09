package com.example.cfchat.service.wiki;

import com.example.cfchat.dto.wiki.WikiPageView;
import com.example.cfchat.repository.wiki.WikiLogRepository;
import com.example.cfchat.repository.wiki.WikiPageHistoryRepository;
import com.example.cfchat.repository.wiki.WikiPageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WikiIntegrationTest {

    @Autowired WikiService wikiService;
    @Autowired WikiPageRepository pageRepo;
    @Autowired WikiPageHistoryRepository historyRepo;
    @Autowired WikiLogRepository logRepo;

    @MockBean WikiEmbeddingService embeddingService;

    @Test
    void writeUpdateUndoInvalidateCycle() {
        UUID user = UUID.randomUUID();
        WikiScope scope = new WikiScope(user, UUID.randomUUID());

        // 1. initial write
        WikiPageView v1 = wikiService.upsert(scope, "facts/coffee", "Coffee",
                "FACT", "loves espresso", "AGENT_WRITE");
        assertThat(pageRepo.findByUserIdAndSlug(user, "facts/coffee")).isPresent();

        // 2. update same slug — should snapshot history
        WikiPageView v2 = wikiService.upsert(scope, "facts/coffee", "Coffee Preferences",
                "FACT", "loves espresso, hates instant", "AGENT_WRITE");
        assertThat(historyRepo.findByPageIdOrderByVersionDesc(v2.id())).hasSize(1);

        // 3. undo — should restore "loves espresso"
        WikiPageView v3 = wikiService.undo(scope, v2.id());
        assertThat(v3.bodyMd()).isEqualTo("loves espresso");

        // 4. log contains WRITE and UNDO operations
        List<String> ops = logRepo.findByUserIdOrderByTsDesc(user, PageRequest.of(0, 10))
                .stream().map(e -> e.getOp()).toList();
        assertThat(ops).contains("UNDO", "WRITE");

        // 5. invalidate
        wikiService.invalidate(scope, "facts/coffee", "old info");
        var after = pageRepo.findByUserIdAndSlug(user, "facts/coffee").orElseThrow();
        assertThat(after.getFrontmatter()).containsKey("invalidated_at");
    }
}
