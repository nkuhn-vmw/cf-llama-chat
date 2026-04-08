// src/main/java/com/example/cfchat/dto/wiki/WikiSearchHit.java
package com.example.cfchat.dto.wiki;

import java.util.UUID;

public record WikiSearchHit(
    UUID pageId, String slug, String title, String kind, String snippet, double score
) {}
