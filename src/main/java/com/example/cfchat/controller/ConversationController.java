package com.example.cfchat.controller;

import com.example.cfchat.auth.UserService;
import com.example.cfchat.dto.ConversationDto;
import com.example.cfchat.dto.MessageDto;
import com.example.cfchat.model.User;
import com.example.cfchat.service.ConversationService;
import com.example.cfchat.service.MarkdownService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
            @RequestBody Map<String, String> body) {
        UUID userId = requireUserId();
        if (!conversationService.isOwnedByUser(id, userId)) {
            return ResponseEntity.status(403).build();
        }
        String title = body.get("title");
        if (title != null) {
            if (title.length() > 500) {
                throw new IllegalArgumentException("Title must not exceed 500 characters");
            }
            conversationService.updateConversationTitle(id, title);
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConversation(@PathVariable UUID id) {
        UUID userId = requireUserId();
        if (!conversationService.isOwnedByUser(id, userId)) {
            return ResponseEntity.status(403).build();
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

    @DeleteMapping("/clear-all")
    public ResponseEntity<Map<String, Object>> clearAllConversations() {
        UUID userId = requireUserId();
        Optional<User> currentUser = userService.getCurrentUser();

        int deletedCount = conversationService.deleteAllConversationsForUser(userId);
        log.info("User {} cleared all {} conversations",
                currentUser.map(User::getUsername).orElse("unknown"), deletedCount);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "deletedCount", deletedCount
        ));
    }

    private UUID requireUserId() {
        return userService.getCurrentUser()
                .map(User::getId)
                .orElseThrow(() -> new IllegalStateException("Authentication required"));
    }
}
