package com.example.cfchat.controller;

import com.example.cfchat.auth.UserService;
import com.example.cfchat.model.PromptPreset;
import com.example.cfchat.model.User;
import com.example.cfchat.service.PromptPresetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/prompts")
@RequiredArgsConstructor
public class PromptPresetController {

    private final PromptPresetService presetService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<PromptPresetDto>> list() {
        UUID userId = getCurrentUserId();
        return ResponseEntity.ok(
                presetService.getAccessiblePresets(userId).stream()
                        .map(this::toDto)
                        .toList()
        );
    }

    @GetMapping("/search")
    public ResponseEntity<List<PromptPresetDto>> search(@RequestParam String q) {
        UUID userId = getCurrentUserId();
        return ResponseEntity.ok(
                presetService.searchPresets(userId, q).stream()
                        .map(this::toDto)
                        .toList()
        );
    }

    @PostMapping
    public ResponseEntity<PromptPresetDto> create(@RequestBody CreatePresetRequest request) {
        UUID userId = getCurrentUserId();
        PromptPreset preset = presetService.create(
                userId, request.command(), request.title(),
                request.content(), request.description(),
                request.shared() != null && request.shared()
        );
        return ResponseEntity.ok(toDto(preset));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PromptPresetDto> update(@PathVariable UUID id, @RequestBody UpdatePresetRequest request) {
        UUID userId = getCurrentUserId();
        PromptPreset preset = presetService.update(
                id, userId, request.command(), request.title(),
                request.content(), request.description(), request.shared()
        );
        return ResponseEntity.ok(toDto(preset));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        UUID userId = getCurrentUserId();
        presetService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }

    private UUID getCurrentUserId() {
        return userService.getCurrentUser()
                .map(User::getId)
                .orElseThrow(() -> new IllegalStateException("Authentication required"));
    }

    private PromptPresetDto toDto(PromptPreset p) {
        return new PromptPresetDto(p.getId(), p.getCommand(), p.getTitle(),
                p.getContent(), p.getDescription(), p.getOwnerId(), p.isShared(), p.getCreatedAt());
    }

    public record PromptPresetDto(UUID id, String command, String title, String content,
                                   String description, UUID ownerId, boolean shared, java.time.LocalDateTime createdAt) {}

    public record CreatePresetRequest(String command, String title, String content, String description, Boolean shared) {}

    public record UpdatePresetRequest(String command, String title, String content, String description, Boolean shared) {}
}
