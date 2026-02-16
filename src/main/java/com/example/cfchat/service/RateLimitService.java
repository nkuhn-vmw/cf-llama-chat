package com.example.cfchat.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class RateLimitService {

    @Value("${rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${rate-limit.roles.USER.chat-per-minute:20}")
    private int userChatPerMinute;

    @Value("${rate-limit.roles.USER.chat-per-hour:200}")
    private int userChatPerHour;

    @Value("${rate-limit.roles.USER.uploads-per-hour:50}")
    private int userUploadsPerHour;

    @Value("${rate-limit.roles.VIEWER.chat-per-minute:10}")
    private int viewerChatPerMinute;

    @Value("${rate-limit.roles.VIEWER.chat-per-hour:60}")
    private int viewerChatPerHour;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public boolean tryConsume(UUID userId, String role, String action) {
        if (!enabled) return true;
        if ("ADMIN".equalsIgnoreCase(role)) return true;

        String key = userId.toString() + ":" + action;
        Bucket bucket = buckets.computeIfAbsent(key, k -> createBucket(role, action));
        return bucket.tryConsume(1);
    }

    public long getRetryAfterSeconds(UUID userId, String role, String action) {
        String key = userId.toString() + ":" + action;
        Bucket bucket = buckets.get(key);
        if (bucket == null) return 0;

        var probe = bucket.tryConsumeAndReturnRemaining(0);
        return probe.getNanosToWaitForRefill() / 1_000_000_000;
    }

    private Bucket createBucket(String role, String action) {
        boolean isViewer = "VIEWER".equalsIgnoreCase(role);

        return switch (action) {
            case "chat" -> Bucket.builder()
                    .addLimit(Bandwidth.simple(
                            isViewer ? viewerChatPerMinute : userChatPerMinute,
                            Duration.ofMinutes(1)))
                    .addLimit(Bandwidth.simple(
                            isViewer ? viewerChatPerHour : userChatPerHour,
                            Duration.ofHours(1)))
                    .build();
            case "upload" -> Bucket.builder()
                    .addLimit(Bandwidth.simple(userUploadsPerHour, Duration.ofHours(1)))
                    .build();
            default -> Bucket.builder()
                    .addLimit(Bandwidth.simple(60, Duration.ofMinutes(1)))
                    .build();
        };
    }

    // Cleanup stale buckets periodically
    public void cleanup() {
        // Buckets auto-refill, so no urgent cleanup needed
        // Could add a scheduled task to remove idle buckets
        if (buckets.size() > 10000) {
            log.info("Rate limit bucket cache size: {}, consider cleanup", buckets.size());
        }
    }
}
