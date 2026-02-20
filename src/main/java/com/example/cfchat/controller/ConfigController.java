package com.example.cfchat.controller;

import com.example.cfchat.auth.UserService;
import com.example.cfchat.model.User;
import com.example.cfchat.service.ConfigExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller for admin config import/export operations.
 */
@RestController
@RequestMapping("/api/admin/config")
@RequiredArgsConstructor
@Slf4j
public class ConfigController {

    private final ConfigExportService configExportService;
    private final UserService userService;

    /**
     * Export system configuration as a downloadable JSON file.
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportConfig() {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).build();
        }

        byte[] configData = configExportService.exportConfig();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String filename = "cf-chat-config-" + timestamp + ".json";

        log.info("Admin {} exported system configuration", currentUser.get().getUsername());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .contentLength(configData.length)
                .body(configData);
    }

    /**
     * Import system configuration from an uploaded JSON file.
     */
    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importConfig(@RequestParam("file") MultipartFile file) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }

        String contentType = file.getContentType();
        if (contentType != null && !contentType.contains("json") && !contentType.equals("application/octet-stream")) {
            return ResponseEntity.badRequest().body(Map.of("error", "File must be a JSON file"));
        }

        try {
            byte[] data = file.getBytes();
            Map<String, Object> result = configExportService.importConfig(data);

            log.info("Admin {} imported system configuration: {}", currentUser.get().getUsername(), result);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to import configuration: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to import configuration: " + e.getMessage(),
                    "success", false
            ));
        }
    }
}
