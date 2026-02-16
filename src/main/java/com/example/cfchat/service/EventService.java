package com.example.cfchat.service;

/**
 * Interface for cluster-wide event broadcasting.
 * When Redis is available, events are published via pub/sub for multi-node coordination.
 * When Redis is not available, a local no-op implementation is used.
 */
public interface EventService {

    /**
     * Broadcast a message to the given channel across all nodes.
     *
     * @param channel the event channel name (e.g., "settings.changed", "models.invalidate")
     * @param message the message payload
     */
    void broadcast(String channel, String message);

    /**
     * Register a listener for events on the given channel.
     *
     * @param channel  the event channel name
     * @param listener callback invoked when a message is received
     */
    void subscribe(String channel, EventListener listener);

    /**
     * Functional interface for event listeners.
     */
    @FunctionalInterface
    interface EventListener {
        void onMessage(String channel, String message);
    }
}
