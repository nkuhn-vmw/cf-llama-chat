package com.example.cfchat.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveUserTrackerTest {

    private ActiveUserTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new ActiveUserTracker();
    }

    @Test
    void record_tracksUserActivity() {
        UUID userId = UUID.randomUUID();

        tracker.record(userId, "testuser", "gpt-4o");

        assertThat(tracker.getActiveCount()).isEqualTo(1);
    }

    @Test
    void record_multipleUsers_allTracked() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        UUID userId3 = UUID.randomUUID();

        tracker.record(userId1, "user1", "gpt-4o");
        tracker.record(userId2, "user2", "llama-3");
        tracker.record(userId3, "user3", "gpt-4o");

        assertThat(tracker.getActiveCount()).isEqualTo(3);
    }

    @Test
    void record_sameUser_updatesSession() {
        UUID userId = UUID.randomUUID();

        tracker.record(userId, "testuser", "gpt-4o");
        tracker.record(userId, "testuser", "llama-3");

        assertThat(tracker.getActiveCount()).isEqualTo(1);

        List<ActiveUserTracker.ActiveSession> sessions = tracker.getActiveSessions();
        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).currentModel()).isEqualTo("llama-3");
    }

    @Test
    void getActiveCount_returnsZero_whenNoUsers() {
        assertThat(tracker.getActiveCount()).isZero();
    }

    @Test
    void getActiveSessions_returnsSessionDetails() {
        UUID userId = UUID.randomUUID();
        tracker.record(userId, "testuser", "gpt-4o");

        List<ActiveUserTracker.ActiveSession> sessions = tracker.getActiveSessions();

        assertThat(sessions).hasSize(1);
        ActiveUserTracker.ActiveSession session = sessions.get(0);
        assertThat(session.userId()).isEqualTo(userId);
        assertThat(session.username()).isEqualTo("testuser");
        assertThat(session.currentModel()).isEqualTo("gpt-4o");
        assertThat(session.lastSeen()).isNotNull();
    }

    @Test
    void getActiveSessions_returnsImmutableList() {
        UUID userId = UUID.randomUUID();
        tracker.record(userId, "testuser", "gpt-4o");

        List<ActiveUserTracker.ActiveSession> sessions = tracker.getActiveSessions();
        try {
            sessions.add(new ActiveUserTracker.ActiveSession(
                    UUID.randomUUID(), "other", Instant.now(), "model"));
            // If we get here, the list is mutable - which would be unexpected
            assertThat(false).as("Expected UnsupportedOperationException").isTrue();
        } catch (UnsupportedOperationException e) {
            // Expected - list is immutable
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void getActiveCount_excludesExpiredSessions() {
        UUID activeUserId = UUID.randomUUID();
        UUID expiredUserId = UUID.randomUUID();

        // Add an active user
        tracker.record(activeUserId, "activeuser", "gpt-4o");

        // Manually inject an expired session via reflection
        ConcurrentHashMap<UUID, ActiveUserTracker.ActiveSession> sessions =
                (ConcurrentHashMap<UUID, ActiveUserTracker.ActiveSession>)
                        ReflectionTestUtils.getField(tracker, "sessions");

        Instant expiredTime = Instant.now().minus(Duration.ofMinutes(20));
        sessions.put(expiredUserId, new ActiveUserTracker.ActiveSession(
                expiredUserId, "expireduser", expiredTime, "model"));

        // Only the active user should be counted
        assertThat(tracker.getActiveCount()).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getActiveSessions_excludesExpiredSessions() {
        UUID activeUserId = UUID.randomUUID();
        UUID expiredUserId = UUID.randomUUID();

        tracker.record(activeUserId, "activeuser", "gpt-4o");

        // Manually inject an expired session via reflection
        ConcurrentHashMap<UUID, ActiveUserTracker.ActiveSession> sessions =
                (ConcurrentHashMap<UUID, ActiveUserTracker.ActiveSession>)
                        ReflectionTestUtils.getField(tracker, "sessions");

        Instant expiredTime = Instant.now().minus(Duration.ofMinutes(20));
        sessions.put(expiredUserId, new ActiveUserTracker.ActiveSession(
                expiredUserId, "expireduser", expiredTime, "model"));

        List<ActiveUserTracker.ActiveSession> activeSessions = tracker.getActiveSessions();
        assertThat(activeSessions).hasSize(1);
        assertThat(activeSessions.get(0).username()).isEqualTo("activeuser");
    }

    @Test
    @SuppressWarnings("unchecked")
    void prune_removesAllExpiredSessions() {
        // Inject only expired sessions via reflection
        ConcurrentHashMap<UUID, ActiveUserTracker.ActiveSession> sessions =
                (ConcurrentHashMap<UUID, ActiveUserTracker.ActiveSession>)
                        ReflectionTestUtils.getField(tracker, "sessions");

        Instant expiredTime = Instant.now().minus(Duration.ofMinutes(30));
        for (int i = 0; i < 5; i++) {
            UUID id = UUID.randomUUID();
            sessions.put(id, new ActiveUserTracker.ActiveSession(
                    id, "user" + i, expiredTime, "model"));
        }

        // After pruning via getActiveCount, all expired sessions should be gone
        assertThat(tracker.getActiveCount()).isZero();
    }

    @Test
    void record_lastSeenIsRecent() {
        UUID userId = UUID.randomUUID();
        Instant before = Instant.now();

        tracker.record(userId, "testuser", "gpt-4o");

        List<ActiveUserTracker.ActiveSession> sessions = tracker.getActiveSessions();
        assertThat(sessions.get(0).lastSeen()).isAfterOrEqualTo(before);
    }
}
