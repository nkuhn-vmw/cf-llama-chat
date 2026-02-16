package com.example.cfchat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Local (single-node) implementation of EventService.
 * Used when Redis is not available. Events are delivered only within the same JVM.
 */
@Service
@Slf4j
@ConditionalOnMissingBean(ClusterEventService.class)
public class LocalEventService implements EventService {

    private final ConcurrentHashMap<String, List<EventListener>> listeners = new ConcurrentHashMap<>();

    @Override
    public void broadcast(String channel, String message) {
        log.debug("Local event broadcast on channel '{}': {}", channel, message);
        List<EventListener> channelListeners = listeners.get(channel);
        if (channelListeners != null) {
            for (EventListener listener : channelListeners) {
                try {
                    listener.onMessage(channel, message);
                } catch (Exception e) {
                    log.warn("Error in local event listener for channel '{}': {}", channel, e.getMessage());
                }
            }
        }
    }

    @Override
    public void subscribe(String channel, EventListener listener) {
        listeners.computeIfAbsent(channel, k -> new CopyOnWriteArrayList<>()).add(listener);
        log.debug("Subscribed local listener to channel '{}'", channel);
    }
}
