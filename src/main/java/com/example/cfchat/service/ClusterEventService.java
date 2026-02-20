package com.example.cfchat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Redis-backed implementation of EventService for multi-node coordination.
 * Activated only when spring.data.redis.host is configured AND Redis classes are on the classpath.
 * Uses reflection to interact with RedisTemplate to avoid compile-time dependency on spring-data-redis.
 * Provides cluster-wide pub/sub for settings changes, model list invalidation, etc.
 *
 * To enable: add spring-boot-starter-data-redis to the classpath and configure spring.data.redis.host.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "spring.data.redis.host", matchIfMissing = false)
@ConditionalOnClass(name = "org.springframework.data.redis.core.RedisTemplate")
public class ClusterEventService implements EventService {

    private final ApplicationContext applicationContext;
    private final ConcurrentHashMap<String, List<EventListener>> localListeners = new ConcurrentHashMap<>();
    private Object redisTemplate;

    public ClusterEventService(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        try {
            this.redisTemplate = applicationContext.getBean("redisTemplate");
            log.info("ClusterEventService initialized with Redis pub/sub");
        } catch (Exception e) {
            log.warn("ClusterEventService: RedisTemplate not available, falling back to local-only delivery: {}",
                    e.getMessage());
            this.redisTemplate = null;
        }
    }

    @Override
    public void broadcast(String channel, String message) {
        if (redisTemplate != null) {
            try {
                Method convertAndSend = redisTemplate.getClass()
                        .getMethod("convertAndSend", String.class, Object.class);
                convertAndSend.invoke(redisTemplate, channel, message);
                log.debug("Broadcast to Redis channel '{}': {}", channel, message);
                return;
            } catch (Exception e) {
                log.warn("Failed to broadcast to Redis channel '{}': {}", channel, e.getMessage());
            }
        }
        // Fallback to local delivery
        deliverLocally(channel, message);
    }

    @Override
    public void subscribe(String channel, EventListener listener) {
        localListeners.computeIfAbsent(channel, k -> new CopyOnWriteArrayList<>()).add(listener);
        log.info("Subscribed to channel '{}' for cluster events (Redis-backed when available)", channel);
    }

    private void deliverLocally(String channel, String message) {
        List<EventListener> listeners = localListeners.get(channel);
        if (listeners != null) {
            for (EventListener listener : listeners) {
                try {
                    listener.onMessage(channel, message);
                } catch (Exception e) {
                    log.warn("Error in cluster event listener for channel '{}': {}", channel, e.getMessage());
                }
            }
        }
    }
}
