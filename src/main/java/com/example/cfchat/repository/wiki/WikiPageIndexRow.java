package com.example.cfchat.repository.wiki;

import java.util.UUID;

public record WikiPageIndexRow(UUID id, String slug, String title, String kind) {}
