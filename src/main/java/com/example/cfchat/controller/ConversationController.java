package com.example.cfchat.controller;

import com.example.cfchat.dto.ConversationDto;
import com.example.cfchat.dto.MessageDto;
import com.example.cfchat.service.ConversationService;
import com.example.cfchat.service.MarkdownService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@Slf4j
public class ConversationController {

    private final ConversationService conversationService;
    private final MarkdownService markdownService;

    @GetMapping
    public ResponseEntity<List<ConversationDto>> getAllConversations() {
        return ResponseEntity.ok(conversationService.getAllConversations());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConversationDto> getConversation(@PathVariable UUID id) {
        return conversationService.getConversation(id)
                .map(conv -> {
                    // Add HTML content to messages
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

        var conversation = conversationService.createConversation(title, provider, model);
        return ResponseEntity.ok(ConversationDto.fromEntity(conversation, false));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> updateConversation(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        String title = body.get("title");
        if (title != null) {
            conversationService.updateConversationTitle(id, title);
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConversation(@PathVariable UUID id) {
        conversationService.deleteConversation(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<List<MessageDto>> getMessages(@PathVariable UUID id) {
        List<MessageDto> messages = conversationService.getMessages(id).stream()
                .map(msg -> {
                    MessageDto dto = MessageDto.fromEntity(msg);
                    dto.setHtmlContent(markdownService.toHtml(msg.getContent()));
                    return dto;
                })
                .toList();
        return ResponseEntity.ok(messages);
    }
}
