package com.example.cfchat.model;

import java.io.Serializable;
import java.util.UUID;

public class ConversationTagLinkId implements Serializable {
    private UUID conversationId;
    private Long tagId;

    public ConversationTagLinkId() {}
    public ConversationTagLinkId(UUID conversationId, Long tagId) {
        this.conversationId = conversationId;
        this.tagId = tagId;
    }
}
