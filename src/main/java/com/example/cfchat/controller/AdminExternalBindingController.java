package com.example.cfchat.controller;

import com.example.cfchat.auth.UserService;
import com.example.cfchat.model.ExternalBinding;
import com.example.cfchat.model.User;
import com.example.cfchat.service.ExternalBindingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/admin/external-bindings")
@Slf4j
public class AdminExternalBindingController {

    private final ExternalBindingService externalBindingService;
    private final UserService userService;

    public AdminExternalBindingController(
            ExternalBindingService externalBindingService,
            UserService userService) {
        this.externalBindingService = externalBindingService;
        this.userService = userService;
    }

    private boolean isAdmin() {
        return userService.getCurrentUser()
                .map(user -> user.getRole() == User.UserRole.ADMIN)
                .orElse(false);
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllBindings() {
        if (!isAdmin()) {
            return ResponseEntity.status(403).build();
        }

        List<ExternalBinding> bindings = externalBindingService.findAll();
        List<Map<String, Object>> result = new ArrayList<>();

        for (ExternalBinding binding : bindings) {
            result.add(toResponseMap(binding));
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getBinding(@PathVariable UUID id) {
        if (!isAdmin()) {
            return ResponseEntity.status(403).build();
        }

        return externalBindingService.findById(id)
                .map(binding -> ResponseEntity.ok(toResponseMap(binding)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createBinding(@RequestBody Map<String, Object> body) {
        if (!isAdmin()) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        String name = (String) body.get("name");
        String apiBase = (String) body.get("apiBase");
        String apiKey = (String) body.get("apiKey");
        String configUrl = (String) body.get("configUrl");
        String description = (String) body.get("description");

        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Name is required"));
        }
        if (apiBase == null || apiBase.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "API Base URL is required"));
        }
        if (apiKey == null || apiKey.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "API Key is required"));
        }

        if (externalBindingService.existsByName(name)) {
            return ResponseEntity.badRequest().body(Map.of("error", "A binding with this name already exists"));
        }

        try {
            ExternalBinding binding = ExternalBinding.builder()
                    .name(name)
                    .apiBase(apiBase)
                    .apiKey(apiKey)
                    .configUrl(configUrl != null && !configUrl.isBlank() ? configUrl : null)
                    .description(description)
                    .enabled(true)
                    .build();

            ExternalBinding saved = externalBindingService.create(binding);
            log.info("Created external binding: {} by admin {}", name,
                    userService.getCurrentUser().map(User::getUsername).orElse("unknown"));

            return ResponseEntity.ok(toResponseMap(saved));

        } catch (Exception e) {
            log.error("Failed to create external binding: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to create binding: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateBinding(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {

        if (!isAdmin()) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        Optional<ExternalBinding> existingOpt = externalBindingService.findById(id);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ExternalBinding existing = existingOpt.get();

        String name = (String) body.get("name");
        String apiBase = (String) body.get("apiBase");
        String apiKey = (String) body.get("apiKey");
        String configUrl = (String) body.get("configUrl");
        String description = (String) body.get("description");

        // Check for name uniqueness if name is changing
        if (name != null && !name.equals(existing.getName())) {
            if (externalBindingService.existsByName(name)) {
                return ResponseEntity.badRequest().body(Map.of("error", "A binding with this name already exists"));
            }
            existing.setName(name);
        }

        if (apiBase != null && !apiBase.isBlank()) {
            existing.setApiBase(apiBase);
        }
        if (apiKey != null && !apiKey.isBlank()) {
            existing.setApiKey(apiKey);
        }
        if (configUrl != null) {
            existing.setConfigUrl(configUrl.isBlank() ? null : configUrl);
        }
        if (description != null) {
            existing.setDescription(description);
        }

        try {
            ExternalBinding saved = externalBindingService.update(existing);
            log.info("Updated external binding: {} by admin {}", saved.getName(),
                    userService.getCurrentUser().map(User::getUsername).orElse("unknown"));

            return ResponseEntity.ok(toResponseMap(saved));

        } catch (Exception e) {
            log.error("Failed to update external binding: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to update binding: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteBinding(@PathVariable UUID id) {
        if (!isAdmin()) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        Optional<ExternalBinding> existingOpt = externalBindingService.findById(id);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        try {
            String name = existingOpt.get().getName();
            externalBindingService.delete(id);
            log.info("Deleted external binding: {} by admin {}", name,
                    userService.getCurrentUser().map(User::getUsername).orElse("unknown"));

            return ResponseEntity.ok(Map.of("success", true));

        } catch (Exception e) {
            log.error("Failed to delete external binding: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to delete binding: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}/enabled")
    public ResponseEntity<Map<String, Object>> toggleEnabled(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {

        if (!isAdmin()) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        Boolean enabled = (Boolean) body.get("enabled");
        if (enabled == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Enabled status is required"));
        }

        try {
            ExternalBinding saved = externalBindingService.setEnabled(id, enabled);
            log.info("Set external binding {} enabled={} by admin {}", saved.getName(), enabled,
                    userService.getCurrentUser().map(User::getUsername).orElse("unknown"));

            return ResponseEntity.ok(toResponseMap(saved));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to toggle external binding enabled: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to update binding: " + e.getMessage()));
        }
    }

    @PostMapping("/{id}/reload")
    public ResponseEntity<Map<String, Object>> reloadModels(@PathVariable UUID id) {
        if (!isAdmin()) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        Optional<ExternalBinding> existingOpt = externalBindingService.findById(id);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ExternalBinding binding = existingOpt.get();
        if (!binding.isEnabled()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot reload disabled binding"));
        }

        try {
            int modelCount = externalBindingService.reloadModelsFromBinding(binding);
            log.info("Reloaded {} models from external binding {} by admin {}", modelCount, binding.getName(),
                    userService.getCurrentUser().map(User::getUsername).orElse("unknown"));

            Map<String, Object> result = toResponseMap(binding);
            result.put("reloadedModelCount", modelCount);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Failed to reload external binding: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to reload binding: " + e.getMessage()));
        }
    }

    /**
     * Convert binding to response map (never expose apiKey).
     */
    private Map<String, Object> toResponseMap(ExternalBinding binding) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", binding.getId());
        map.put("name", binding.getName());
        map.put("description", binding.getDescription());
        map.put("apiBase", binding.getApiBase());
        map.put("configUrl", binding.getConfigUrl());
        map.put("enabled", binding.isEnabled());
        map.put("createdAt", binding.getCreatedAt());
        map.put("updatedAt", binding.getUpdatedAt());

        // Add model information
        int modelCount = externalBindingService.getModelCountForBinding(binding.getId());
        Set<String> modelNames = externalBindingService.getModelNamesForBinding(binding.getId());
        map.put("modelCount", modelCount);
        map.put("modelNames", modelNames);

        // Determine type based on configUrl presence
        map.put("type", binding.getConfigUrl() != null ? "Locator" : "Direct");

        return map;
    }
}
