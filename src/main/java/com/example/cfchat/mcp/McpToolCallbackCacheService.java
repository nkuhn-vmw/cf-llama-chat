package com.example.cfchat.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.McpToolsChangedEvent;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class McpToolCallbackCacheService implements ApplicationListener<McpToolsChangedEvent> {

    private static final Logger logger = LoggerFactory.getLogger(McpToolCallbackCacheService.class);

    private final List<McpServerService> mcpServerServices;
    private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();
    private final AtomicBoolean cacheInvalidated = new AtomicBoolean(true);

    private volatile ToolCallbackProvider[] cachedToolCallbacks = null;

    public McpToolCallbackCacheService(List<McpServerService> mcpServerServices) {
        this.mcpServerServices = mcpServerServices;
        logger.info("McpToolCallbackCacheService initialized with {} MCP server services",
                mcpServerServices.size());
    }

    public ToolCallbackProvider[] getToolCallbackProviders() {
        if (!cacheInvalidated.get() && cachedToolCallbacks != null) {
            logger.debug("Returning cached tool callbacks ({} providers)", cachedToolCallbacks.length);
            return cachedToolCallbacks;
        }

        cacheLock.writeLock().lock();
        try {
            if (!cacheInvalidated.get() && cachedToolCallbacks != null) {
                logger.debug("Cache was refreshed by another thread, returning cached callbacks");
                return cachedToolCallbacks;
            }

            logger.info("Refreshing MCP tool callback cache for {} server(s)", mcpServerServices.size());

            List<ToolCallbackProvider> providers = mcpServerServices.stream()
                    .map(this::createToolCallbackProvider)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();

            cachedToolCallbacks = providers.toArray(new ToolCallbackProvider[0]);
            cacheInvalidated.set(false);

            logger.info("MCP tool callback cache refreshed with {} provider(s) from {} configured server(s)",
                    cachedToolCallbacks.length, mcpServerServices.size());

            return cachedToolCallbacks;

        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    private Optional<ToolCallbackProvider> createToolCallbackProvider(McpServerService serverService) {
        try {
            logger.debug("Creating session-recovering tool callback provider for {} ({})",
                    serverService.getName(), serverService.getProtocol().displayName());

            ToolCallbackProvider provider = new SessionRecoveringToolCallbackProvider(
                    serverService.getName(),
                    serverService::createMcpSyncClient
            );

            logger.info("Successfully created tool callback provider for {} ({})",
                    serverService.getName(), serverService.getProtocol().displayName());
            return Optional.of(provider);

        } catch (Exception e) {
            logger.warn("MCP server {} ({}) is currently unavailable and will be skipped: {}",
                    serverService.getName(), serverService.getProtocol().displayName(), e.getMessage());
            logger.debug("Full stack trace for unavailable MCP server {}: ", serverService.getName(), e);
            return Optional.empty();
        }
    }

    @Override
    public void onApplicationEvent(McpToolsChangedEvent event) {
        logger.info("Received McpToolsChangedEvent, invalidating tool callback cache");
        invalidateCache();
    }

    public void invalidateCache() {
        cacheLock.writeLock().lock();
        try {
            cacheInvalidated.set(true);
            logger.debug("Tool callback cache invalidated");
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    public boolean isCacheValid() {
        return !cacheInvalidated.get() && cachedToolCallbacks != null;
    }

    public List<McpServerService> getMcpServerServices() {
        return List.copyOf(mcpServerServices);
    }
}
