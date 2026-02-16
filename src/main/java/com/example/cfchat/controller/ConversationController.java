package com.example.cfchat.controller;

import com.example.cfchat.auth.UserService;
import com.example.cfchat.dto.ConversationDto;
import com.example.cfchat.dto.MessageDto;
import com.example.cfchat.model.User;
import com.example.cfchat.model.Conversation;
import com.example.cfchat.model.Message;
import com.example.cfchat.repository.ConversationRepository;
import com.example.cfchat.repository.MessageRepository;
import com.example.cfchat.service.ConversationService;
import com.example.cfchat.service.MarkdownService;
import com.example.cfchat.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@Slf4j
public class ConversationController {

    private final ConversationService conversationService;
    private final MarkdownService markdownService;
    private final UserService userService;
    private final SystemSettingService systemSettingService;
    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;

    @GetMapping
    public ResponseEntity<List<ConversationDto>> getAllConversations() {
        UUID userId = requireUserId();
        return ResponseEntity.ok(conversationService.getConversationsForUser(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConversationDto> getConversation(@PathVariable UUID id) {
        UUID userId = requireUserId();
        return conversationService.getConversationForUser(id, userId)
                .map(conv -> {
                    if (conv.getMessages() != null) {
                        conv.getMessages().forEach(msg ->
                                msg.setHtmlContent(markdownService.toHtml(msg.getContent())));
                    }
                    return ResponseEntity.ok(conv);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ConversationDto> createConversation(@RequestBody(required = false) Map<String, String> body) {
        String title = body != null ? body.get("title") : null;
        String provider = body != null ? body.get("provider") : null;
        String model = body != null ? body.get("model") : null;

        if (title != null && title.length() > 500) {
            throw new IllegalArgumentException("Title must not exceed 500 characters");
        }

        UUID userId = requireUserId();
        var conversation = conversationService.createConversation(title, provider, model, userId);
        return ResponseEntity.ok(ConversationDto.fromEntity(conversation, false));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> updateConversation(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        UUID userId = requireUserId();
        if (!conversationService.isOwnedByUser(id, userId)) {
            return ResponseEntity.status(403).build();
        }
        Object titleObj = body.get("title");
        if (titleObj != null) {
            String title = titleObj.toString();
            if (title.length() > 500) {
                throw new IllegalArgumentException("Title must not exceed 500 characters");
            }
            conversationService.updateConversationTitle(id, title);
        }
        if (body.containsKey("pinned")) {
            boolean pinned = Boolean.parseBoolean(body.get("pinned").toString());
            conversationService.pinConversation(id, pinned);
        }
        if (body.containsKey("archived")) {
            boolean archived = Boolean.parseBoolean(body.get("archived").toString());
            if (archived) {
                conversationService.archiveConversation(id);
            } else {
                conversationService.unarchiveConversation(id);
            }
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConversation(@PathVariable UUID id) {
        UUID userId = requireUserId();
        if (!conversationService.isOwnedByUser(id, userId)) {
            return ResponseEntity.status(403).build();
        }
        // Check if chat deletion is prevented for non-admin users
        if (systemSettingService.getBooleanSetting("prevent_chat_deletion", false)) {
            Optional<User> currentUser = userService.getCurrentUser();
            if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
                log.warn("Non-admin user {} attempted to delete conversation {} while deletion is prevented",
                        currentUser.map(User::getUsername).orElse("unknown"), id);
                return ResponseEntity.status(403).build();
            }
        }
        conversationService.deleteConversation(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<List<MessageDto>> getMessages(@PathVariable UUID id) {
        UUID userId = requireUserId();
        if (!conversationService.isOwnedByUser(id, userId)) {
            return ResponseEntity.status(403).build();
        }
        List<MessageDto> messages = conversationService.getMessages(id).stream()
                .map(msg -> {
                    MessageDto dto = MessageDto.fromEntity(msg);
                    dto.setHtmlContent(markdownService.toHtml(msg.getContent()));
                    return dto;
                })
                .toList();
        return ResponseEntity.ok(messages);
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<Void> archive(@PathVariable UUID id) {
        UUID userId = requireUserId();
        if (!conversationService.isOwnedByUser(id, userId)) {
            return ResponseEntity.status(403).build();
        }
        conversationService.archiveConversation(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/unarchive")
    public ResponseEntity<Void> unarchive(@PathVariable UUID id) {
        UUID userId = requireUserId();
        if (!conversationService.isOwnedByUser(id, userId)) {
            return ResponseEntity.status(403).build();
        }
        conversationService.unarchiveConversation(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/archive-all")
    public ResponseEntity<Map<String, Integer>> archiveAll() {
        UUID userId = requireUserId();
        int count = conversationService.archiveAllForUser(userId);
        return ResponseEntity.ok(Map.of("archivedCount", count));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<ConversationDto>> search(@RequestParam String q, Pageable pageable) {
        UUID userId = requireUserId();
        return ResponseEntity.ok(conversationService.searchConversations(userId, q, pageable));
    }

    @GetMapping("/pinned")
    public ResponseEntity<List<ConversationDto>> pinned() {
        UUID userId = requireUserId();
        return ResponseEntity.ok(conversationService.getPinnedConversations(userId));
    }

    @GetMapping("/archived")
    public ResponseEntity<Page<ConversationDto>> archived(Pageable pageable) {
        UUID userId = requireUserId();
        return ResponseEntity.ok(conversationService.getArchivedConversations(userId, pageable));
    }

    @DeleteMapping("/clear-all")
    public ResponseEntity<Map<String, Object>> clearAllConversations() {
        UUID userId = requireUserId();
        Optional<User> currentUser = userService.getCurrentUser();

        // Check if chat deletion is prevented for non-admin users
        if (systemSettingService.getBooleanSetting("prevent_chat_deletion", false)) {
            if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
                log.warn("Non-admin user {} attempted to clear all conversations while deletion is prevented",
                        currentUser.map(User::getUsername).orElse("unknown"));
                return ResponseEntity.status(403).body(Map.of(
                        "error", "Chat deletion is currently disabled by administrator"
                ));
            }
        }

        int deletedCount = conversationService.deleteAllConversationsForUser(userId);
        log.info("User {} cleared all {} conversations",
                currentUser.map(User::getUsername).orElse("unknown"), deletedCount);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "deletedCount", deletedCount
        ));
    }

    @PostMapping("/{id}/clone")
    public ResponseEntity<?> cloneConversation(@PathVariable UUID id, @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.findByUsername(userDetails.getUsername()).orElseThrow();
        Conversation cloned = conversationService.cloneConversation(id, user.getId());
        return ResponseEntity.ok(Map.of("id", cloned.getId(), "title", cloned.getTitle()));
    }

    @PatchMapping("/{convId}/messages/{msgId}/favorite")
    public ResponseEntity<?> toggleFavorite(@PathVariable UUID convId, @PathVariable UUID msgId) {
        Message msg = messageRepository.findById(msgId)
            .orElseThrow(() -> new IllegalArgumentException("Message not found"));
        msg.setFavorited(!Boolean.TRUE.equals(msg.getFavorited()));
        messageRepository.save(msg);
        return ResponseEntity.ok(Map.of("favorited", msg.getFavorited()));
    }

    @GetMapping("/favorites")
    public ResponseEntity<?> getFavorites(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.findByUsername(userDetails.getUsername()).orElseThrow();
        List<Conversation> convs = conversationRepository.findByUserIdOrderByUpdatedAtDesc(user.getId());
        List<UUID> convIds = convs.stream().map(Conversation::getId).toList();
        List<Message> favorites = messageRepository.findByConversationIdInAndFavoritedTrue(convIds);
        return ResponseEntity.ok(favorites.stream().map(m -> Map.of(
            "id", m.getId(),
            "content", m.getContent(),
            "role", m.getRole().name(),
            "conversationId", m.getConversation().getId(),
            "createdAt", m.getCreatedAt()
        )).toList());
    }

    private UUID requireUserId() {
        return userService.getCurrentUser()
                .map(User::getId)
                .orElseThrow(() -> new IllegalStateException("Authentication required"));
    }
}
