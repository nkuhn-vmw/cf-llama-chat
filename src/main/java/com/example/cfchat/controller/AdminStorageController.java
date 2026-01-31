package com.example.cfchat.controller;

import com.example.cfchat.auth.UserService;
import com.example.cfchat.model.DocumentStorageConfig;
import com.example.cfchat.model.User;
import com.example.cfchat.service.DocumentStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Admin controller for managing document storage configuration (S3).
 */
@RestController
@RequestMapping("/api/admin/storage")
@RequiredArgsConstructor
@Slf4j
public class AdminStorageController {

    private final DocumentStorageService storageService;
    private final UserService userService;

    /**
     * Get the current storage configuration.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getStorageConfig() {
        if (!isAdmin()) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        Optional<DocumentStorageConfig> configOpt = storageService.getConfiguration();
        Map<String, Object> response = new HashMap<>();

        if (configOpt.isPresent()) {
            DocumentStorageConfig config = configOpt.get();
            response.put("configured", true);
            response.put("enabled", config.isEnabled());
            response.put("endpointUrl", config.getEndpointUrl());
            response.put("bucketName", config.getBucketName());
            response.put("region", config.getRegion());
            response.put("pathPrefix", config.getPathPrefix());
            response.put("pathStyleAccess", config.isPathStyleAccess());
            // Don't expose access key and secret key
            response.put("hasAccessKey", config.getAccessKey() != null && !config.getAccessKey().isBlank());
            response.put("hasSecretKey", config.getSecretKey() != null && !config.getSecretKey().isBlank());
            response.put("updatedAt", config.getUpdatedAt());
        } else {
            response.put("configured", false);
            response.put("enabled", false);
        }

        response.put("storageActive", storageService.isStorageEnabled());

        return ResponseEntity.ok(response);
    }

    /**
     * Save storage configuration.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> saveStorageConfig(@RequestBody Map<String, Object> body) {
        if (!isAdmin()) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        try {
            // Get existing config or create new one
            DocumentStorageConfig config = storageService.getConfiguration()
                    .orElse(new DocumentStorageConfig());

            // Update fields from request
            if (body.containsKey("enabled")) {
                config.setEnabled(Boolean.TRUE.equals(body.get("enabled")));
            }
            if (body.containsKey("endpointUrl")) {
                config.setEndpointUrl((String) body.get("endpointUrl"));
            }
            if (body.containsKey("bucketName")) {
                config.setBucketName((String) body.get("bucketName"));
            }
            if (body.containsKey("region")) {
                config.setRegion((String) body.get("region"));
            }
            if (body.containsKey("pathPrefix")) {
                config.setPathPrefix((String) body.get("pathPrefix"));
            }
            if (body.containsKey("pathStyleAccess")) {
                config.setPathStyleAccess(Boolean.TRUE.equals(body.get("pathStyleAccess")));
            }
            // Only update credentials if provided (non-empty)
            if (body.containsKey("accessKey")) {
                String accessKey = (String) body.get("accessKey");
                if (accessKey != null && !accessKey.isBlank()) {
                    config.setAccessKey(accessKey);
                }
            }
            if (body.containsKey("secretKey")) {
                String secretKey = (String) body.get("secretKey");
                if (secretKey != null && !secretKey.isBlank()) {
                    config.setSecretKey(secretKey);
                }
            }

            DocumentStorageConfig saved = storageService.saveConfiguration(config);
            log.info("Admin {} updated storage configuration", getCurrentUsername());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "enabled", saved.isEnabled(),
                    "storageActive", storageService.isStorageEnabled()
            ));

        } catch (Exception e) {
            log.error("Failed to save storage configuration: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to save configuration",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Test the S3 connection with provided or saved configuration.
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testConnection(@RequestBody Map<String, Object> body) {
        if (!isAdmin()) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        try {
            // Build test config from request
            DocumentStorageConfig testConfig = storageService.getConfiguration()
                    .map(existing -> {
                        // Start with existing config
                        DocumentStorageConfig config = DocumentStorageConfig.builder()
                                .endpointUrl(existing.getEndpointUrl())
                                .bucketName(existing.getBucketName())
                                .region(existing.getRegion())
                                .accessKey(existing.getAccessKey())
                                .secretKey(existing.getSecretKey())
                                .pathStyleAccess(existing.isPathStyleAccess())
                                .build();
                        return config;
                    })
                    .orElse(new DocumentStorageConfig());

            // Override with request values if provided
            if (body.containsKey("endpointUrl")) {
                testConfig.setEndpointUrl((String) body.get("endpointUrl"));
            }
            if (body.containsKey("bucketName")) {
                testConfig.setBucketName((String) body.get("bucketName"));
            }
            if (body.containsKey("region")) {
                testConfig.setRegion((String) body.get("region"));
            }
            if (body.containsKey("pathStyleAccess")) {
                testConfig.setPathStyleAccess(Boolean.TRUE.equals(body.get("pathStyleAccess")));
            }
            if (body.containsKey("accessKey")) {
                String accessKey = (String) body.get("accessKey");
                if (accessKey != null && !accessKey.isBlank()) {
                    testConfig.setAccessKey(accessKey);
                }
            }
            if (body.containsKey("secretKey")) {
                String secretKey = (String) body.get("secretKey");
                if (secretKey != null && !secretKey.isBlank()) {
                    testConfig.setSecretKey(secretKey);
                }
            }

            // Test connection
            String errorMessage = storageService.testConnection(testConfig);

            if (errorMessage == null) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Successfully connected to S3 bucket"
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "message", errorMessage
                ));
            }

        } catch (Exception e) {
            log.error("Failed to test S3 connection: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Connection test failed: " + e.getMessage()
            ));
        }
    }

    /**
     * Disable storage (quick toggle).
     */
    @PostMapping("/disable")
    public ResponseEntity<Map<String, Object>> disableStorage() {
        if (!isAdmin()) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        Optional<DocumentStorageConfig> configOpt = storageService.getConfiguration();
        if (configOpt.isPresent()) {
            DocumentStorageConfig config = configOpt.get();
            config.setEnabled(false);
            storageService.saveConfiguration(config);
            log.info("Admin {} disabled storage", getCurrentUsername());
            return ResponseEntity.ok(Map.of("success", true, "enabled", false));
        }

        return ResponseEntity.ok(Map.of("success", true, "enabled", false));
    }

    /**
     * Enable storage (quick toggle, requires valid configuration).
     */
    @PostMapping("/enable")
    public ResponseEntity<Map<String, Object>> enableStorage() {
        if (!isAdmin()) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        Optional<DocumentStorageConfig> configOpt = storageService.getConfiguration();
        if (configOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "No storage configuration found",
                    "message", "Please configure S3 settings first"
            ));
        }

        DocumentStorageConfig config = configOpt.get();

        // Test connection before enabling
        String errorMessage = storageService.testConnection(config);
        if (errorMessage != null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Cannot enable storage",
                    "message", errorMessage
            ));
        }

        config.setEnabled(true);
        storageService.saveConfiguration(config);
        log.info("Admin {} enabled storage", getCurrentUsername());

        return ResponseEntity.ok(Map.of("success", true, "enabled", true));
    }

    private boolean isAdmin() {
        return userService.getCurrentUser()
                .map(user -> user.getRole() == User.UserRole.ADMIN)
                .orElse(false);
    }

    private String getCurrentUsername() {
        return userService.getCurrentUser()
                .map(User::getUsername)
                .orElse("unknown");
    }
}
