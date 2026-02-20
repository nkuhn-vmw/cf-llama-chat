package com.example.cfchat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class ContentModerationService {

    @Value("${moderation.enabled:false}")
    private boolean enabled;

    @Value("${moderation.action:warn}")
    private String action; // warn or block

    public record ModerationResult(boolean flagged, String reason, String action) {}

    public boolean isEnabled() { return enabled; }

    public ModerationResult check(String text) {
        if (!enabled) return new ModerationResult(false, null, null);

        // Pattern-based detection
        if (containsToxicPatterns(text)) {
            return new ModerationResult(true, "Content flagged by pattern detection", action);
        }
        return new ModerationResult(false, null, null);
    }

    private boolean containsToxicPatterns(String text) {
        // Basic pattern matching - in production use ML model or API
        String lower = text.toLowerCase();
        List<String> patterns = List.of(
            "kill yourself", "how to make a bomb", "how to hack"
        );
        return patterns.stream().anyMatch(lower::contains);
    }
}
