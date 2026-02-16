package com.example.cfchat.controller;

import com.example.cfchat.auth.UserService;
import com.example.cfchat.model.ConversationTag;
import com.example.cfchat.model.ConversationTagLink;
import com.example.cfchat.model.User;
import com.example.cfchat.repository.ConversationTagLinkRepository;
import com.example.cfchat.repository.ConversationTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final ConversationTagRepository tagRepo;
    private final ConversationTagLinkRepository linkRepo;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<TagDto>> list() {
        UUID userId = getCurrentUserId();
        return ResponseEntity.ok(tagRepo.findByUserId(userId).stream()
                .map(t -> new TagDto(t.getId(), t.getName(), t.getColor())).toList());
    }

    @PostMapping
    public ResponseEntity<TagDto> create(@RequestBody CreateTagRequest req) {
        UUID userId = getCurrentUserId();
        ConversationTag tag = ConversationTag.builder()
                .userId(userId).name(req.name()).color(req.color()).build();
        tag = tagRepo.save(tag);
        return ResponseEntity.ok(new TagDto(tag.getId(), tag.getName(), tag.getColor()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        tagRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{tagId}/conversations/{convId}")
    public ResponseEntity<Void> tagConversation(@PathVariable Long tagId, @PathVariable UUID convId) {
        linkRepo.save(new ConversationTagLink(convId, tagId));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{tagId}/conversations/{convId}")
    public ResponseEntity<Void> untagConversation(@PathVariable Long tagId, @PathVariable UUID convId) {
        linkRepo.deleteByConversationIdAndTagId(convId, tagId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{tagId}/conversations")
    public ResponseEntity<List<UUID>> getTaggedConversations(@PathVariable Long tagId) {
        return ResponseEntity.ok(linkRepo.findConversationIdsByTagId(tagId));
    }

    private UUID getCurrentUserId() {
        return userService.getCurrentUser().map(User::getId)
                .orElseThrow(() -> new IllegalStateException("Authentication required"));
    }

    public record TagDto(Long id, String name, String color) {}
    public record CreateTagRequest(String name, String color) {}
}
