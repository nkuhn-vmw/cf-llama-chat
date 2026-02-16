package com.example.cfchat.controller;

import com.example.cfchat.auth.UserService;
import com.example.cfchat.model.ChatFolder;
import com.example.cfchat.model.User;
import com.example.cfchat.repository.ChatFolderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/folders")
@RequiredArgsConstructor
public class ChatFolderController {

    private final ChatFolderRepository folderRepo;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<FolderDto>> list() {
        UUID userId = getCurrentUserId();
        return ResponseEntity.ok(folderRepo.findByUserIdOrderBySortOrderAsc(userId)
                .stream().map(this::toDto).toList());
    }

    @PostMapping
    public ResponseEntity<FolderDto> create(@RequestBody CreateFolderRequest req) {
        UUID userId = getCurrentUserId();
        ChatFolder folder = ChatFolder.builder()
                .userId(userId).name(req.name())
                .parentFolderId(req.parentFolderId())
                .sortOrder(req.sortOrder() != null ? req.sortOrder() : 0)
                .build();
        return ResponseEntity.ok(toDto(folderRepo.save(folder)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FolderDto> update(@PathVariable UUID id, @RequestBody UpdateFolderRequest req) {
        ChatFolder folder = folderRepo.findById(id).orElseThrow();
        if (!folder.getUserId().equals(getCurrentUserId())) throw new SecurityException("Access denied");
        if (req.name() != null) folder.setName(req.name());
        if (req.sortOrder() != null) folder.setSortOrder(req.sortOrder());
        return ResponseEntity.ok(toDto(folderRepo.save(folder)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        ChatFolder folder = folderRepo.findById(id).orElseThrow();
        if (!folder.getUserId().equals(getCurrentUserId())) throw new SecurityException("Access denied");
        folderRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private UUID getCurrentUserId() {
        return userService.getCurrentUser().map(User::getId)
                .orElseThrow(() -> new IllegalStateException("Authentication required"));
    }

    private FolderDto toDto(ChatFolder f) {
        return new FolderDto(f.getId(), f.getName(), f.getParentFolderId(), f.getSortOrder(), f.getCreatedAt());
    }

    public record FolderDto(UUID id, String name, UUID parentFolderId, int sortOrder, java.time.LocalDateTime createdAt) {}
    public record CreateFolderRequest(String name, UUID parentFolderId, Integer sortOrder) {}
    public record UpdateFolderRequest(String name, Integer sortOrder) {}
}
