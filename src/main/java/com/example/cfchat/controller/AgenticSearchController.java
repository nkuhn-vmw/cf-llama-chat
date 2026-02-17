package com.example.cfchat.controller;

import com.example.cfchat.auth.UserService;
import com.example.cfchat.dto.AgenticSearchRequest;
import com.example.cfchat.dto.AgenticSearchResponse;
import com.example.cfchat.model.User;
import com.example.cfchat.service.AgenticSearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST controller for the Agentic Search feature.
 * Only loaded when AgenticSearchService is available (i.e., agentic-search.enabled=true).
 */
@RestController
@RequestMapping("/api/agentic-search")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnBean(AgenticSearchService.class)
public class AgenticSearchController {

    private final AgenticSearchService agenticSearchService;
    private final UserService userService;

    /**
     * Execute an agentic search with multi-step query decomposition and synthesis.
     */
    @PostMapping
    public ResponseEntity<AgenticSearchResponse> search(@Valid @RequestBody AgenticSearchRequest request) {
        UUID userId = userService.getCurrentUser().map(User::getId).orElse(null);
        if (userId == null) {
            return ResponseEntity.status(401).body(
                    AgenticSearchResponse.builder()
                            .originalQuery(request.getQuery())
                            .error("Authentication required")
                            .build());
        }

        log.info("Agentic search request from user {}: query='{}', maxIterations={}, includeWeb={}",
                userId,
                request.getQuery().length() > 100
                        ? request.getQuery().substring(0, 100) + "..."
                        : request.getQuery(),
                request.getMaxIterations(),
                request.isIncludeWebSearch());

        AgenticSearchResponse response = agenticSearchService.search(request, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Check whether agentic search is enabled and available.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        boolean enabled = agenticSearchService.isEnabled();
        return ResponseEntity.ok(Map.of(
                "enabled", enabled,
                "available", true
        ));
    }
}
