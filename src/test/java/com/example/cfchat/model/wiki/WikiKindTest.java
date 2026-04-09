package com.example.cfchat.model.wiki;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class WikiKindTest {

    @Test
    void agentVisibleReturnsOnlySixKinds() {
        Set<WikiKind> visible = WikiKind.agentVisible();
        assertEquals(6, visible.size());
        assertTrue(visible.contains(WikiKind.ENTITY));
        assertTrue(visible.contains(WikiKind.CONCEPT));
        assertTrue(visible.contains(WikiKind.FACT));
        assertTrue(visible.contains(WikiKind.PREFERENCE));
        assertTrue(visible.contains(WikiKind.DECISION));
        assertTrue(visible.contains(WikiKind.EVENT));
        assertFalse(visible.contains(WikiKind.INDEX));
        assertFalse(visible.contains(WikiKind.LOG));
        assertFalse(visible.contains(WikiKind.NOTE));
    }

    @Test
    void parseAcceptsValidAndRejectsInvalid() {
        assertEquals(WikiKind.FACT, WikiKind.parse("FACT"));
        assertThrows(IllegalArgumentException.class, () -> WikiKind.parse("BANANA"));
    }
}
