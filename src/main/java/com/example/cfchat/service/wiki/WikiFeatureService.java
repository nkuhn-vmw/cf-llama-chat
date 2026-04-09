package com.example.cfchat.service.wiki;

import com.example.cfchat.model.User;
import com.example.cfchat.repository.UserRepository;
import com.example.cfchat.service.CacheInvalidationService;
import com.example.cfchat.service.EventService;
import com.example.cfchat.service.SystemSettingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Two-layer enable/disable for the LLM Wiki feature.
 *
 * <ul>
 *   <li><b>Admin gate</b> — {@code SystemSetting} key {@code wiki.enabled}.
 *       Hard kill switch. When false, no user (admin or otherwise) gets the
 *       feature: tools are not registered, the index block is not injected,
 *       REST endpoints return 404, and the workspace UI is hidden.</li>
 *   <li><b>User opt-out</b> — {@code UserPreferences} key {@code wikiEnabled}.
 *       Personal autonomy. When false, the agent will not call wiki tools
 *       in that user's sessions and the wiki index block is not injected,
 *       but the user can still browse and delete their existing pages.</li>
 * </ul>
 *
 * Both default to {@code true} so existing deployments are unaffected.
 */
@Service
@Slf4j
public class WikiFeatureService {

    public static final String ADMIN_KEY = "wiki.enabled";
    public static final String USER_PREF_KEY = "wikiEnabled";

    private final SystemSettingService systemSettingService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private EventService eventService;

    /**
     * 60 s cache on the admin gate. Chat requests hit this hot path on every
     * turn, so we trade a bounded staleness window for one DB roundtrip per
     * minute. Single-key cache so updates from the admin UI propagate quickly
     * and {@link #invalidateAdminCache()} can drop it on writes if needed.
     */
    private final Cache<String, Boolean> adminGateCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(1)
            .build();

    public WikiFeatureService(SystemSettingService systemSettingService,
                              UserRepository userRepository,
                              ObjectMapper objectMapper) {
        this.systemSettingService = systemSettingService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    /** Org-wide kill switch. Defaults to enabled. Cached for 60 s. */
    public boolean isAdminEnabled() {
        Boolean cached = adminGateCache.get(ADMIN_KEY,
                k -> systemSettingService.getBooleanSetting(ADMIN_KEY, true));
        return cached == null || cached;
    }

    /** Drop the admin-gate cache so a settings write takes effect immediately. */
    public void invalidateAdminCache() {
        adminGateCache.invalidateAll();
    }

    /**
     * Subscribe to the cluster-wide settings invalidation channel so that any
     * write to wiki.enabled — whether from AdminController, an admin script,
     * or another instance of the app — invalidates the cache here. Without
     * this, the explicit AdminController hook only covers one call site and
     * leaves multi-instance deployments serving stale state for up to 60 s.
     */
    @PostConstruct
    void subscribeToSettingsChanges() {
        if (eventService == null) return;
        eventService.subscribe(CacheInvalidationService.CHANNEL_SETTINGS, (channel, message) -> {
            if (ADMIN_KEY.equals(message)) {
                log.info("wiki.enabled changed cluster-wide; invalidating admin gate cache");
                invalidateAdminCache();
            }
        });
    }

    /** Per-user opt-out. Defaults to enabled. Returns true if the prefs blob lacks the key. */
    public boolean isEnabledForUser(UUID userId) {
        if (userId == null) return false;
        if (!isAdminEnabled()) return false;
        return userRepository.findById(userId)
                .map(this::readUserPreference)
                .orElse(true);
    }

    /** Read the user's stored opt-out from their JSON preferences blob. */
    public boolean readUserPreference(User user) {
        String json = user.getPreferences();
        if (json == null || json.isBlank()) return true;
        try {
            Map<String, Object> prefs = objectMapper.readValue(json, new TypeReference<>() {});
            Object v = prefs.get(USER_PREF_KEY);
            if (v == null) return true;
            if (v instanceof Boolean b) return b;
            return Boolean.parseBoolean(String.valueOf(v));
        } catch (Exception e) {
            log.warn("Failed to parse user preferences for wiki opt-out: {}", e.getMessage());
            return true;
        }
    }
}
