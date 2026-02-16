package com.example.cfchat.controller;

import com.example.cfchat.auth.UserService;
import com.example.cfchat.model.User;
import com.example.cfchat.model.UserMemory;
import com.example.cfchat.service.MemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/memory")
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryService memoryService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<MemoryDto>> list() {
        UUID userId = getCurrentUserId();
        return ResponseEntity.ok(memoryService.getUserMemories(userId).stream().map(this::toDto).toList());
    }

    @PostMapping
    public ResponseEntity<MemoryDto> add(@RequestBody CreateMemoryRequest req) {
        UUID userId = getCurrentUserId();
        UserMemory memory = memoryService.addMemory(userId, req.content(), req.category());
        return ResponseEntity.ok(toDto(memory));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MemoryDto> update(@PathVariable UUID id, @RequestBody UpdateMemoryRequest req) {
        UUID userId = getCurrentUserId();
        UserMemory memory = memoryService.updateMemory(id, userId, req.content(), req.category());
        return ResponseEntity.ok(toDto(memory));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        UUID userId = getCurrentUserId();
        memoryService.deleteMemory(id, userId);
        return ResponseEntity.noContent().build();
    }

    private UUID getCurrentUserId() {
        return userService.getCurrentUser().map(User::getId)
                .orElseThrow(() -> new IllegalStateException("Authentication required"));
    }

    private MemoryDto toDto(UserMemory m) {
        return new MemoryDto(m.getId(), m.getContent(), m.getCategory(), m.getCreatedAt(), m.getUpdatedAt());
    }

    public record MemoryDto(UUID id, String content, String category, java.time.LocalDateTime createdAt, java.time.LocalDateTime updatedAt) {}
    public record CreateMemoryRequest(String content, String category) {}
    public record UpdateMemoryRequest(String content, String category) {}
}
