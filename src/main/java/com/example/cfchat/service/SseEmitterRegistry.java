package com.example.cfchat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class SseEmitterRegistry {

    private final ConcurrentHashMap<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(UUID conversationId, long timeout) {
        SseEmitter emitter = new SseEmitter(timeout);
        emitters.put(conversationId, emitter);

        emitter.onCompletion(() -> {
            emitters.remove(conversationId);
            log.debug("SSE emitter completed for conversation: {}", conversationId);
        });
        emitter.onTimeout(() -> {
            emitters.remove(conversationId);
            log.debug("SSE emitter timed out for conversation: {}", conversationId);
        });
        emitter.onError(ex -> {
            emitters.remove(conversationId);
            log.debug("SSE emitter error for conversation: {}: {}", conversationId, ex.getMessage());
        });

        return emitter;
    }

    public void trySend(UUID conversationId, String data) {
        SseEmitter emitter = emitters.get(conversationId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().data(data));
            } catch (Exception ex) {
                emitters.remove(conversationId);
                log.debug("Failed to send SSE data, removing emitter for: {}", conversationId);
            }
        }
    }

    public void trySendEvent(UUID conversationId, String name, Object data) {
        SseEmitter emitter = emitters.get(conversationId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name(name).data(data));
            } catch (Exception ex) {
                emitters.remove(conversationId);
            }
        }
    }

    public void complete(UUID conversationId) {
        SseEmitter emitter = emitters.remove(conversationId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception ignored) {}
        }
    }

    public void completeWithError(UUID conversationId, Exception error) {
        SseEmitter emitter = emitters.remove(conversationId);
        if (emitter != null) {
            try {
                emitter.completeWithError(error);
            } catch (Exception ignored) {}
        }
    }

    public boolean isConnected(UUID conversationId) {
        return emitters.containsKey(conversationId);
    }

    public int getActiveConnectionCount() {
        return emitters.size();
    }
}
