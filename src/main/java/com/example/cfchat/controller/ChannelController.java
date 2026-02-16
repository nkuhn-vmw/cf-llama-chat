package com.example.cfchat.controller;

import com.example.cfchat.auth.UserService;
import com.example.cfchat.model.Channel;
import com.example.cfchat.model.ChannelMessage;
import com.example.cfchat.model.Message;
import com.example.cfchat.model.User;
import com.example.cfchat.service.ChannelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/channels")
@RequiredArgsConstructor
@Slf4j
public class ChannelController {

    private final ChannelService channelService;
    private final UserService userService;

    // SSE emitters per channel for real-time message streaming
    private final ConcurrentHashMap<UUID, List<SseEmitter>> channelEmitters = new ConcurrentHashMap<>();

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listChannels() {
        List<Channel> channels = channelService.listChannels();
        List<Map<String, Object>> result = channels.stream()
                .map(this::toChannelMap)
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createChannel(@RequestBody Map<String, String> body) {
        UUID userId = requireUserId();
        String name = body.get("name");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Channel name is required"));
        }
        if (name.length() > 100) {
            return ResponseEntity.badRequest().body(Map.of("error", "Channel name must not exceed 100 characters"));
        }
        String description = body.get("description");
        String modelId = body.get("modelId");

        Channel channel = channelService.createChannel(name, description, modelId, userId);
        return ResponseEntity.ok(toChannelMap(channel));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getChannel(@PathVariable UUID id) {
        return channelService.getChannel(id)
                .map(channel -> ResponseEntity.ok(toChannelMap(channel)))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteChannel(@PathVariable UUID id) {
        User user = requireUser();
        boolean isAdmin = user.getRole() == User.UserRole.ADMIN;
        boolean deleted = channelService.deleteChannel(id, user.getId(), isAdmin);
        if (!deleted) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<List<Map<String, Object>>> getMessages(@PathVariable UUID id) {
        if (channelService.getChannel(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        List<ChannelMessage> messages = channelService.getMessages(id);
        List<Map<String, Object>> result = messages.stream()
                .map(this::toMessageMap)
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<Map<String, Object>> postMessage(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        User user = requireUser();
        if (channelService.getChannel(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String content = body.get("content");
        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Message content is required"));
        }

        String username = user.getDisplayName() != null ? user.getDisplayName() : user.getUsername();
        ChannelMessage message = channelService.addMessage(
                id, user.getId(), username, content, Message.MessageRole.USER);

        Map<String, Object> messageMap = toMessageMap(message);

        // Broadcast to all SSE listeners for this channel
        broadcastToChannel(id, messageMap);

        return ResponseEntity.ok(messageMap);
    }

    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessages(@PathVariable UUID id) {
        SseEmitter emitter = new SseEmitter(0L); // No timeout

        channelEmitters.computeIfAbsent(id, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(emitter);

        emitter.onCompletion(() -> removeEmitter(id, emitter));
        emitter.onTimeout(() -> removeEmitter(id, emitter));
        emitter.onError(e -> removeEmitter(id, emitter));

        log.debug("New SSE connection for channel {}", id);
        return emitter;
    }

    private void broadcastToChannel(UUID channelId, Map<String, Object> messageData) {
        List<SseEmitter> emitters = channelEmitters.get(channelId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        List<SseEmitter> deadEmitters = new ArrayList<>();
        synchronized (emitters) {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("message")
                            .data(messageData));
                } catch (Exception e) {
                    deadEmitters.add(emitter);
                }
            }
            emitters.removeAll(deadEmitters);
        }
    }

    private void removeEmitter(UUID channelId, SseEmitter emitter) {
        List<SseEmitter> emitters = channelEmitters.get(channelId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                channelEmitters.remove(channelId);
            }
        }
    }

    private Map<String, Object> toChannelMap(Channel channel) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", channel.getId());
        map.put("name", channel.getName());
        map.put("description", channel.getDescription());
        map.put("modelId", channel.getModelId());
        map.put("createdBy", channel.getCreatedBy());
        map.put("createdAt", channel.getCreatedAt());
        return map;
    }

    private Map<String, Object> toMessageMap(ChannelMessage msg) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", msg.getId());
        map.put("channelId", msg.getChannel().getId());
        map.put("userId", msg.getUserId());
        map.put("username", msg.getUsername());
        map.put("content", msg.getContent());
        map.put("role", msg.getRole());
        map.put("createdAt", msg.getCreatedAt());
        return map;
    }

    private UUID requireUserId() {
        return userService.getCurrentUser()
                .map(User::getId)
                .orElseThrow(() -> new IllegalStateException("Authentication required"));
    }

    private User requireUser() {
        return userService.getCurrentUser()
                .orElseThrow(() -> new IllegalStateException("Authentication required"));
    }
}
