package com.example.cfchat.controller;

import com.example.cfchat.auth.UserService;
import com.example.cfchat.model.User;
import com.example.cfchat.model.UserNote;
import com.example.cfchat.repository.UserNoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notes")
@RequiredArgsConstructor
public class NoteController {

    private final UserNoteRepository noteRepo;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<NoteDto>> list() {
        UUID userId = getCurrentUserId();
        return ResponseEntity.ok(noteRepo.findByUserIdOrderByUpdatedAtDesc(userId)
                .stream().map(this::toDto).toList());
    }

    @PostMapping
    public ResponseEntity<NoteDto> create(@RequestBody CreateNoteRequest req) {
        UUID userId = getCurrentUserId();
        UserNote note = UserNote.builder()
                .userId(userId).title(req.title()).content(req.content())
                .conversationId(req.conversationId()).build();
        return ResponseEntity.ok(toDto(noteRepo.save(note)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<NoteDto> update(@PathVariable UUID id, @RequestBody UpdateNoteRequest req) {
        UserNote note = noteRepo.findById(id).orElseThrow();
        if (!note.getUserId().equals(getCurrentUserId())) throw new SecurityException("Access denied");
        if (req.title() != null) note.setTitle(req.title());
        if (req.content() != null) note.setContent(req.content());
        return ResponseEntity.ok(toDto(noteRepo.save(note)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        UserNote note = noteRepo.findById(id).orElseThrow();
        if (!note.getUserId().equals(getCurrentUserId())) throw new SecurityException("Access denied");
        noteRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private UUID getCurrentUserId() {
        return userService.getCurrentUser().map(User::getId)
                .orElseThrow(() -> new IllegalStateException("Authentication required"));
    }

    private NoteDto toDto(UserNote n) {
        return new NoteDto(n.getId(), n.getTitle(), n.getContent(), n.getConversationId(), n.getCreatedAt(), n.getUpdatedAt());
    }

    public record NoteDto(UUID id, String title, String content, UUID conversationId, java.time.LocalDateTime createdAt, java.time.LocalDateTime updatedAt) {}
    public record CreateNoteRequest(String title, String content, UUID conversationId) {}
    public record UpdateNoteRequest(String title, String content) {}
}
