// src/main/java/com/example/cfchat/dto/wiki/WikiPageView.java
package com.example.cfchat.dto.wiki;

import java.time.Instant;
import java.util.UUID;

public record WikiPageView(
    UUID id, UUID userId, String slug, String title, String kind, String origin,
    String bodyMd, int version, Instant createdAt, Instant updatedAt,
    String embeddingStatus
) {}
