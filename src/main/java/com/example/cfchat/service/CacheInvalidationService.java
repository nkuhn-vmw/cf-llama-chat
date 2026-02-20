package com.example.cfchat.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Subscribes to cluster-wide events for cache invalidation.
 * Ensures that settings changes, model list updates, and other
 * shared state is kept consistent across nodes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CacheInvalidationService {

    private final EventService eventService;
    private final SystemSettingService systemSettingService;

    // Simple generation counters to let callers detect staleness
    private final AtomicLong settingsGeneration = new AtomicLong(0);
    private final AtomicLong modelsGeneration = new AtomicLong(0);

    // Local cache for frequently accessed settings
    private final ConcurrentHashMap<String, String> settingsCache = new ConcurrentHashMap<>();

    public static final String CHANNEL_SETTINGS = "cache.settings";
    public static final String CHANNEL_MODELS = "cache.models";
    public static final String CHANNEL_USERS = "cache.users";

    @PostConstruct
    void init() {
        eventService.subscribe(CHANNEL_SETTINGS, (channel, message) -> {
            log.info("Settings cache invalidated by cluster event: {}", message);
            settingsCache.clear();
            settingsGeneration.incrementAndGet();
        });

        eventService.subscribe(CHANNEL_MODELS, (channel, message) -> {
            log.info("Models cache invalidated by cluster event: {}", message);
            modelsGeneration.incrementAndGet();
        });

        eventService.subscribe(CHANNEL_USERS, (channel, message) -> {
            log.debug("User cache event: {}", message);
        });

        log.info("CacheInvalidationService initialized with event subscriptions");
    }

    /** Broadcast a settings change to all nodes. */
    public void notifySettingsChanged(String settingKey) {
        eventService.broadcast(CHANNEL_SETTINGS, settingKey);
    }

    /** Broadcast a model list change to all nodes. */
    public void notifyModelsChanged() {
        eventService.broadcast(CHANNEL_MODELS, "invalidate");
    }

    /** Broadcast a user change (e.g., role update) to all nodes. */
    public void notifyUserChanged(String userId) {
        eventService.broadcast(CHANNEL_USERS, userId);
    }

    /** Get a cached setting value, falling through to the DB if missing. */
    public String getCachedSetting(String key, String defaultValue) {
        return settingsCache.computeIfAbsent(key,
                k -> systemSettingService.getSetting(k, defaultValue));
    }

    public long getSettingsGeneration() {
        return settingsGeneration.get();
    }

    public long getModelsGeneration() {
        return modelsGeneration.get();
    }
}
