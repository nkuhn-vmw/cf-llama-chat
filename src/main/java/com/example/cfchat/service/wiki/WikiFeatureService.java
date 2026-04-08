package com.example.cfchat.service.wiki;

import com.example.cfchat.model.User;
import com.example.cfchat.repository.UserRepository;
import com.example.cfchat.service.SystemSettingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    public WikiFeatureService(SystemSettingService systemSettingService,
                              UserRepository userRepository,
                              ObjectMapper objectMapper) {
        this.systemSettingService = systemSettingService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    /** Org-wide kill switch. Defaults to enabled. */
    public boolean isAdminEnabled() {
        return systemSettingService.getBooleanSetting(ADMIN_KEY, true);
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
