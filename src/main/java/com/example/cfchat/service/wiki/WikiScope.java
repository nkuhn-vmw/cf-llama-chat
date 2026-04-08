package com.example.cfchat.service.wiki;

import org.springframework.ai.chat.model.ToolContext;

import java.util.UUID;

public record WikiScope(UUID userId, UUID conversationId) {

    public static WikiScope from(ToolContext ctx) {
        if (ctx == null) throw new IllegalStateException("ToolContext is null");
        Object u = ctx.getContext().get("userId");
        if (u == null) throw new IllegalStateException("ToolContext missing userId");
        UUID userId = coerce(u);

        Object c = ctx.getContext().get("conversationId");
        UUID convId = c == null ? null : coerce(c);

        return new WikiScope(userId, convId);
    }

    private static UUID coerce(Object v) {
        if (v instanceof UUID u) return u;
        return UUID.fromString(v.toString());
    }
}
