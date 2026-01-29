package com.example.cfchat.controller;

import com.example.cfchat.auth.UserService;
import com.example.cfchat.model.User;
import com.example.cfchat.service.MetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
@Slf4j
public class MetricsController {

    private final MetricsService metricsService;
    private final UserService userService;

    @GetMapping("/metrics")
    public String metricsPage(Model model) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty()) {
            return "redirect:/login.html";
        }

        User user = currentUser.get();
        boolean isAdmin = user.getRole() == User.UserRole.ADMIN;

        model.addAttribute("currentUser", user);
        model.addAttribute("isAdmin", isAdmin);

        // Get user's own metrics
        Map<String, Object> userSummary = metricsService.getSummaryForUser(user.getId());
        model.addAttribute("userSummary", userSummary);

        // If admin, also get global metrics
        if (isAdmin) {
            Map<String, Object> globalSummary = metricsService.getGlobalSummary();
            model.addAttribute("globalSummary", globalSummary);
        }

        return "metrics";
    }

    @GetMapping("/api/metrics/summary")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getSummary() {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        Map<String, Object> summary = metricsService.getSummaryForUser(currentUser.get().getId());
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/api/metrics/global")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getGlobalSummary() {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        if (currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).build();
        }

        Map<String, Object> summary = metricsService.getGlobalSummary();
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/api/metrics/models")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getModelStats() {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        Map<String, Object> stats = metricsService.getModelPerformanceStats();
        return ResponseEntity.ok(stats);
    }
}
