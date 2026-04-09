// src/main/java/com/example/cfchat/event/WikiOpEvent.java
package com.example.cfchat.event;

import com.example.cfchat.dto.wiki.WikiOpPayload;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

public class WikiOpEvent extends ApplicationEvent {
    private final UUID userId;
    private final UUID conversationId;   // may be null for UI-triggered ops
    private final WikiOpPayload payload;

    public WikiOpEvent(Object source, UUID userId, UUID conversationId, WikiOpPayload payload) {
        super(source);
        this.userId = userId;
        this.conversationId = conversationId;
        this.payload = payload;
    }

    public UUID getUserId() { return userId; }
    public UUID getConversationId() { return conversationId; }
    public WikiOpPayload getPayload() { return payload; }
}
