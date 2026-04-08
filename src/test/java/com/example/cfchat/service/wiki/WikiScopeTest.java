package com.example.cfchat.service.wiki;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class WikiScopeTest {

    @Test
    void extractsUserIdAndConversationId() {
        UUID user = UUID.randomUUID();
        UUID conv = UUID.randomUUID();
        ToolContext ctx = new ToolContext(Map.of("userId", user, "conversationId", conv));

        WikiScope scope = WikiScope.from(ctx);

        assertThat(scope.userId()).isEqualTo(user);
        assertThat(scope.conversationId()).isEqualTo(conv);
    }

    @Test
    void missingUserIdThrows() {
        ToolContext ctx = new ToolContext(Map.of());
        assertThatThrownBy(() -> WikiScope.from(ctx))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("userId");
    }

    @Test
    void acceptsUserIdAsString() {
        UUID user = UUID.randomUUID();
        ToolContext ctx = new ToolContext(Map.of("userId", user.toString()));
        WikiScope scope = WikiScope.from(ctx);
        assertThat(scope.userId()).isEqualTo(user);
        assertThat(scope.conversationId()).isNull();
    }
}
