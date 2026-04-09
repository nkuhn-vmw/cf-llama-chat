// src/main/java/com/example/cfchat/dto/wiki/WikiIndexEntry.java
package com.example.cfchat.dto.wiki;

import java.util.UUID;

public record WikiIndexEntry(UUID id, String slug, String title, String kind) {}
