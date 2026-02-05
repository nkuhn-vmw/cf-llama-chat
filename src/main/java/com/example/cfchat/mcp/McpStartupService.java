package com.example.cfchat.mcp;

import com.example.cfchat.model.McpServer;
import com.example.cfchat.service.McpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class McpStartupService {

    private final McpService mcpService;
    private final McpToolCallbackCacheService mcpToolCallbackCacheService;

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void autoConnectMcpServers() {
        List<McpServer> enabledServers = mcpService.getEnabledServers();

        if (enabledServers.isEmpty()) {
            log.info("No enabled MCP servers to auto-connect on startup");
            return;
        }

        log.info("Auto-connecting {} enabled MCP server(s) on startup (async)", enabledServers.size());

        int connected = 0;
        for (McpServer server : enabledServers) {
            try {
                log.info("Auto-connecting MCP server: {} ({})", server.getName(), server.getTransportType());
                McpService.McpConnectionState state = mcpService.connect(server.getId());

                if (state.status() == McpService.ConnectionStatus.CONNECTED) {
                    connected++;
                    try {
                        var tools = mcpService.syncTools(server.getId());
                        log.info("Auto-connected MCP server '{}' with {} tools",
                                server.getName(), tools.size());
                    } catch (Exception e) {
                        log.warn("Connected to MCP server '{}' but failed to sync tools: {}",
                                server.getName(), e.getMessage());
                    }
                } else {
                    log.warn("Failed to auto-connect MCP server '{}': {}",
                            server.getName(), state.errorMessage());
                }
            } catch (Exception e) {
                log.warn("Error auto-connecting MCP server '{}': {}",
                        server.getName(), e.getMessage());
            }
        }

        if (connected > 0) {
            mcpToolCallbackCacheService.invalidateCache();
        }

        log.info("MCP startup auto-connect complete: {}/{} servers connected",
                connected, enabledServers.size());
    }
}
