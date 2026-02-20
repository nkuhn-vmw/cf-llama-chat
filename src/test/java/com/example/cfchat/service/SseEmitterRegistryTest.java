package com.example.cfchat.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SseEmitterRegistryTest {

    private SseEmitterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SseEmitterRegistry();
    }

    @Test
    void register_returnsNewEmitter() {
        UUID convId = UUID.randomUUID();

        SseEmitter emitter = registry.register(convId, 30000L);

        assertThat(emitter).isNotNull();
    }

    @Test
    void register_makesConversationConnected() {
        UUID convId = UUID.randomUUID();

        registry.register(convId, 30000L);

        assertThat(registry.isConnected(convId)).isTrue();
    }

    @Test
    void register_incrementsActiveConnectionCount() {
        assertThat(registry.getActiveConnectionCount()).isZero();

        registry.register(UUID.randomUUID(), 30000L);
        assertThat(registry.getActiveConnectionCount()).isEqualTo(1);

        registry.register(UUID.randomUUID(), 30000L);
        assertThat(registry.getActiveConnectionCount()).isEqualTo(2);
    }

    @Test
    void register_sameId_replacesExistingEmitter() {
        UUID convId = UUID.randomUUID();

        SseEmitter first = registry.register(convId, 30000L);
        SseEmitter second = registry.register(convId, 30000L);

        assertThat(second).isNotSameAs(first);
        assertThat(registry.getActiveConnectionCount()).isEqualTo(1);
    }

    @Test
    void isConnected_unregisteredId_returnsFalse() {
        UUID convId = UUID.randomUUID();

        assertThat(registry.isConnected(convId)).isFalse();
    }

    @Test
    void complete_removesEmitter() {
        UUID convId = UUID.randomUUID();
        registry.register(convId, 30000L);

        registry.complete(convId);

        assertThat(registry.isConnected(convId)).isFalse();
        assertThat(registry.getActiveConnectionCount()).isZero();
    }

    @Test
    void complete_nonExistentId_doesNotThrow() {
        UUID convId = UUID.randomUUID();

        // Should not throw
        registry.complete(convId);

        assertThat(registry.getActiveConnectionCount()).isZero();
    }

    @Test
    void completeWithError_removesEmitter() {
        UUID convId = UUID.randomUUID();
        registry.register(convId, 30000L);

        registry.completeWithError(convId, new RuntimeException("test error"));

        assertThat(registry.isConnected(convId)).isFalse();
    }

    @Test
    void completeWithError_nonExistentId_doesNotThrow() {
        UUID convId = UUID.randomUUID();

        // Should not throw
        registry.completeWithError(convId, new RuntimeException("test"));

        assertThat(registry.getActiveConnectionCount()).isZero();
    }

    @Test
    void trySend_nonExistentId_doesNotThrow() {
        UUID convId = UUID.randomUUID();

        // Should not throw
        registry.trySend(convId, "some data");
    }

    @Test
    void trySendEvent_nonExistentId_doesNotThrow() {
        UUID convId = UUID.randomUUID();

        // Should not throw
        registry.trySendEvent(convId, "event-name", "data");
    }

    @Test
    void getActiveConnectionCount_multipleRegistrations_returnsCorrectCount() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();

        registry.register(id1, 30000L);
        registry.register(id2, 30000L);
        registry.register(id3, 30000L);

        assertThat(registry.getActiveConnectionCount()).isEqualTo(3);
    }

    @Test
    void complete_onlyRemovesTargetEmitter() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        registry.register(id1, 30000L);
        registry.register(id2, 30000L);

        registry.complete(id1);

        assertThat(registry.isConnected(id1)).isFalse();
        assertThat(registry.isConnected(id2)).isTrue();
        assertThat(registry.getActiveConnectionCount()).isEqualTo(1);
    }

    @Test
    void register_withDifferentTimeouts_works() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        SseEmitter emitter1 = registry.register(id1, 5000L);
        SseEmitter emitter2 = registry.register(id2, 60000L);

        assertThat(emitter1).isNotNull();
        assertThat(emitter2).isNotNull();
        assertThat(registry.getActiveConnectionCount()).isEqualTo(2);
    }
}
