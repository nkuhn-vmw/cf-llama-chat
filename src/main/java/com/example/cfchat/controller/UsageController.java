package com.example.cfchat.controller;

import com.example.cfchat.auth.UserService;
import com.example.cfchat.model.User;
import com.example.cfchat.service.MetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class UsageController {

    private final MetricsService metricsService;
    private final UserService userService;

    @GetMapping("/api/user/usage")
    public ResponseEntity<Map<String, Object>> getMyUsage() {
        UUID userId = userService.getCurrentUser().map(User::getId)
                .orElseThrow(() -> new IllegalStateException("Authentication required"));
        return ResponseEntity.ok(metricsService.getSummaryForUser(userId));
    }

    @GetMapping("/api/admin/usage")
    public ResponseEntity<Map<String, Object>> getAdminUsage(
            @RequestParam(required = false) String modelId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok(metricsService.getGlobalSummary());
    }
}
