package com.example.cfchat.model.wiki;

import java.util.EnumSet;
import java.util.Set;

public enum WikiKind {
    // Agent-visible: the LLM may write these via wiki_write.
    ENTITY, CONCEPT, FACT, PREFERENCE, DECISION, EVENT,
    // System-only: never exposed to the LLM.
    INDEX, LOG, NOTE;

    private static final Set<WikiKind> AGENT_VISIBLE =
        EnumSet.of(ENTITY, CONCEPT, FACT, PREFERENCE, DECISION, EVENT);

    public static Set<WikiKind> agentVisible() {
        return EnumSet.copyOf(AGENT_VISIBLE);
    }

    public static WikiKind parse(String raw) {
        if (raw == null) throw new IllegalArgumentException("WikiKind is null");
        return WikiKind.valueOf(raw.trim().toUpperCase());
    }
}
