// src/test/java/com/example/cfchat/tools/wiki/WikiToolsTest.java
package com.example.cfchat.tools.wiki;

import com.example.cfchat.dto.wiki.WikiPageView;
import com.example.cfchat.service.wiki.WikiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WikiToolsTest {

    WikiService service;
    WikiTools tools;

    @BeforeEach
    void setUp() {
        service = mock(WikiService.class);
        tools = new WikiTools(service);
    }

    @Test
    void searchPassesScopeFromToolContext() {
        UUID user = UUID.randomUUID();
        ToolContext ctx = new ToolContext(Map.of("userId", user));
        when(service.search(any(), eq("q"), isNull(), eq(6)))
            .thenReturn(List.of());

        tools.wikiSearch("q", null, null, ctx);

        verify(service).search(argThat(s -> s.userId().equals(user)), eq("q"), isNull(), eq(6));
    }

    @Test
    void writeRejectsSystemOnlyKind() {
        ToolContext ctx = new ToolContext(Map.of("userId", UUID.randomUUID()));
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            tools.wikiWrite("x", "t", "NOTE", "body", ctx))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not agent-writable");
    }

    @Test
    void writeAcceptsAgentVisibleKind() {
        ToolContext ctx = new ToolContext(Map.of("userId", UUID.randomUUID()));
        when(service.upsert(any(), eq("x"), eq("t"), eq("FACT"), eq("body"), eq("AGENT_WRITE")))
            .thenReturn(new WikiPageView(UUID.randomUUID(), UUID.randomUUID(),
                "x", "t", "FACT", "AGENT_WRITE", "body", 1,
                Instant.now(), Instant.now(), "PENDING"));
        tools.wikiWrite("x", "t", "FACT", "body", ctx);
        verify(service).upsert(any(), eq("x"), eq("t"), eq("FACT"), eq("body"), eq("AGENT_WRITE"));
    }

    @Test
    void searchCapsKAt20() {
        ToolContext ctx = new ToolContext(Map.of("userId", UUID.randomUUID()));
        tools.wikiSearch("q", null, 999, ctx);
        verify(service).search(any(), eq("q"), isNull(), eq(20));
    }
}
