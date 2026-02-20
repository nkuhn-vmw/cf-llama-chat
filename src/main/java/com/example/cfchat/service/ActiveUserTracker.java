package com.example.cfchat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class ActiveUserTracker {

    private final ConcurrentHashMap<UUID, ActiveSession> sessions = new ConcurrentHashMap<>();
    private static final Duration ACTIVE_WINDOW = Duration.ofMinutes(15);

    public record ActiveSession(UUID userId, String username, Instant lastSeen, String currentModel) {}

    public void record(UUID userId, String username, String modelId) {
        sessions.put(userId, new ActiveSession(userId, username, Instant.now(), modelId));
    }

    public int getActiveCount() {
        prune();
        return sessions.size();
    }

    public List<ActiveSession> getActiveSessions() {
        prune();
        return List.copyOf(sessions.values());
    }

    private void prune() {
        Instant cutoff = Instant.now().minus(ACTIVE_WINDOW);
        sessions.entrySet().removeIf(e -> e.getValue().lastSeen().isBefore(cutoff));
    }
}
