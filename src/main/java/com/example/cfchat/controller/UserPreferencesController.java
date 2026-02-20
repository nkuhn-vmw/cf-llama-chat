package com.example.cfchat.controller;

import com.example.cfchat.auth.UserService;
import com.example.cfchat.model.User;
import com.example.cfchat.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST controller for managing user preferences (theme, background, language, etc.).
 * Preferences are stored as a JSON string in the user's 'preferences' column.
 */
@RestController
@RequestMapping("/api/user/preferences")
@RequiredArgsConstructor
@Slf4j
public class UserPreferencesController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    /**
     * Available preset background identifiers.
     */
    private static final List<String> PRESET_BACKGROUNDS = List.of(
            "none", "gradient-blue", "gradient-purple", "gradient-dark",
            "pattern-dots", "pattern-grid", "nature-mountain", "nature-ocean"
    );

    /**
     * Get the current user's preferences.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getPreferences() {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        Map<String, Object> prefs = parsePreferences(currentUser.get().getPreferences());
        return ResponseEntity.ok(prefs);
    }

    /**
     * Update the current user's preferences (merges with existing).
     */
    @PutMapping
    public ResponseEntity<Map<String, Object>> updatePreferences(@RequestBody Map<String, Object> newPrefs) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        User user = currentUser.get();
        Map<String, Object> existingPrefs = parsePreferences(user.getPreferences());
        existingPrefs.putAll(newPrefs);
        savePreferences(user, existingPrefs);

        log.debug("Updated preferences for user {}: {}", user.getUsername(), newPrefs.keySet());
        return ResponseEntity.ok(existingPrefs);
    }

    /**
     * Set the user's theme preference (light/dark/oled).
     */
    @PutMapping("/theme")
    public ResponseEntity<Map<String, Object>> setTheme(@RequestBody Map<String, String> body) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        String theme = body.get("theme");
        if (theme == null || !List.of("light", "dark", "oled").contains(theme)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid theme. Must be light, dark, or oled"));
        }

        User user = currentUser.get();
        Map<String, Object> prefs = parsePreferences(user.getPreferences());
        prefs.put("theme", theme);
        savePreferences(user, prefs);

        log.debug("User {} set theme to {}", user.getUsername(), theme);
        return ResponseEntity.ok(Map.of("success", true, "theme", theme));
    }

    /**
     * Set the user's chat background preference.
     * Accepts a preset name or a custom URL.
     */
    @PutMapping("/background")
    public ResponseEntity<Map<String, Object>> setBackground(@RequestBody Map<String, String> body) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        String background = body.get("background");
        if (background == null || background.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Background value is required"));
        }

        // Validate: must be a known preset or a URL starting with https://
        boolean isPreset = PRESET_BACKGROUNDS.contains(background);
        boolean isUrl = background.startsWith("https://");
        if (!isPreset && !isUrl) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Background must be a preset name or an HTTPS URL",
                    "presets", PRESET_BACKGROUNDS
            ));
        }

        User user = currentUser.get();
        Map<String, Object> prefs = parsePreferences(user.getPreferences());
        prefs.put("background", background);
        savePreferences(user, prefs);

        log.debug("User {} set background to {}", user.getUsername(), background);
        return ResponseEntity.ok(Map.of("success", true, "background", background));
    }

    /**
     * Set the user's language preference.
     */
    @PutMapping("/language")
    public ResponseEntity<Map<String, Object>> setLanguage(@RequestBody Map<String, String> body) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        String language = body.get("language");
        if (language == null || language.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Language code is required"));
        }

        // Validate against supported locales
        Set<String> supported = Set.of("en", "es", "fr", "de", "ja", "zh");
        if (!supported.contains(language)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Unsupported language. Supported: " + supported,
                    "supported", supported
            ));
        }

        User user = currentUser.get();
        Map<String, Object> prefs = parsePreferences(user.getPreferences());
        prefs.put("language", language);
        savePreferences(user, prefs);

        log.debug("User {} set language to {}", user.getUsername(), language);
        return ResponseEntity.ok(Map.of("success", true, "language", language));
    }

    /**
     * Get available background presets.
     */
    @GetMapping("/backgrounds")
    public ResponseEntity<List<String>> getBackgroundPresets() {
        return ResponseEntity.ok(PRESET_BACKGROUNDS);
    }

    /**
     * Allowed RAG retrieval modes.
     */
    private static final List<String> RAG_RETRIEVAL_MODES = List.of("snippet", "full");

    /**
     * Set the user's RAG retrieval mode preference.
     * "snippet" returns individual matched text chunks (default).
     * "full" returns the entire parent document when a chunk matches.
     */
    @PutMapping("/rag-retrieval-mode")
    public ResponseEntity<Map<String, Object>> setRagRetrievalMode(@RequestBody Map<String, String> body) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        String mode = body.get("ragRetrievalMode");
        if (mode == null || !RAG_RETRIEVAL_MODES.contains(mode)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid RAG retrieval mode. Must be one of: " + RAG_RETRIEVAL_MODES,
                    "validModes", RAG_RETRIEVAL_MODES
            ));
        }

        User user = currentUser.get();
        Map<String, Object> prefs = parsePreferences(user.getPreferences());
        prefs.put("ragRetrievalMode", mode);
        savePreferences(user, prefs);

        log.debug("User {} set RAG retrieval mode to {}", user.getUsername(), mode);
        return ResponseEntity.ok(Map.of("success", true, "ragRetrievalMode", mode));
    }

    // --- Helpers ---

    private Map<String, Object> parsePreferences(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse user preferences JSON: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private void savePreferences(User user, Map<String, Object> prefs) {
        try {
            user.setPreferences(objectMapper.writeValueAsString(prefs));
            userRepository.save(user);
        } catch (Exception e) {
            log.error("Failed to save user preferences: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save preferences", e);
        }
    }
}
