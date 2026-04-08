// src/main/java/com/example/cfchat/dto/wiki/WikiOpPayload.java
package com.example.cfchat.dto.wiki;

import java.util.UUID;

public record WikiOpPayload(
    String op,         // WRITE | LINK | INVALIDATE | UNDO
    UUID pageId,
    String slug,
    String title,
    String kind,
    String summary
) {}
