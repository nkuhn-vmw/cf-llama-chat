package com.example.cfchat.mcp;

import com.example.cfchat.model.McpServer;
import com.example.cfchat.service.McpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.McpToolsChangedEvent;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class McpToolCallbackCacheService implements ApplicationListener<McpToolsChangedEvent> {

    private static final Logger logger = LoggerFactory.getLogger(McpToolCallbackCacheService.class);

    private final List<McpServerService> mcpServerServices;
    private final McpService mcpService;
    private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();
    private final AtomicBoolean cacheInvalidated = new AtomicBoolean(true);

    private volatile ToolCallbackProvider[] cachedToolCallbacks = null;

    public McpToolCallbackCacheService(List<McpServerService> mcpServerServices, McpService mcpService) {
        this.mcpServerServices = mcpServerServices;
        this.mcpService = mcpService;
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

            List<ToolCallbackProvider> providers = new ArrayList<>();

            // 1. Add providers from CF service binding MCP servers
            logger.info("Refreshing MCP tool callback cache for {} CF-bound server(s)", mcpServerServices.size());
            mcpServerServices.stream()
                    .map(this::createToolCallbackProvider)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .forEach(providers::add);

            // 2. Add providers from admin-configured MCP servers
            List<McpServer> enabledServers = mcpService.getEnabledServers();
            logger.info("Checking {} admin-configured MCP server(s) for active connections", enabledServers.size());
            for (McpServer server : enabledServers) {
                createAdminToolCallbackProvider(server.getId(), server.getName())
                        .ifPresent(providers::add);
            }

            cachedToolCallbacks = providers.toArray(new ToolCallbackProvider[0]);
            cacheInvalidated.set(false);

            logger.info("MCP tool callback cache refreshed with {} provider(s) ({} CF-bound + {} admin-configured)",
                    cachedToolCallbacks.length, mcpServerServices.size(), enabledServers.size());

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

    private Optional<ToolCallbackProvider> createAdminToolCallbackProvider(UUID serverId, String serverName) {
        return mcpService.getActiveClient(serverId).map(client -> {
            try {
                ToolCallbackProvider provider = new SyncMcpToolCallbackProvider(client);
                logger.info("Created tool callback provider for admin MCP server: {}", serverName);
                return provider;
            } catch (Exception e) {
                logger.warn("Failed to create tool callback provider for admin MCP server {}: {}",
                        serverName, e.getMessage());
                return null;
            }
        });
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
