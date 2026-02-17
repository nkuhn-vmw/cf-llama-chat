package com.example.cfchat.controller;

import com.example.cfchat.service.AgenticSearchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Fallback controller for /api/agentic-search when the feature is disabled.
 * Returns a clear error instead of letting requests fall through to the static resource handler.
 */
@RestController
@RequestMapping("/api/agentic-search")
@ConditionalOnMissingBean(AgenticSearchService.class)
public class AgenticSearchFallbackController {

    @PostMapping
    public ResponseEntity<Map<String, Object>> search() {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Agent Search is not enabled. Set agentic-search.enabled=true to activate."
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "enabled", false,
                "available", false
        ));
    }
}
