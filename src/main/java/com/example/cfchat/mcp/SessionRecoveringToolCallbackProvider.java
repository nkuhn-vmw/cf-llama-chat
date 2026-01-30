package com.example.cfchat.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class SessionRecoveringToolCallbackProvider implements ToolCallbackProvider {

    private static final Logger logger = LoggerFactory.getLogger(SessionRecoveringToolCallbackProvider.class);

    private final String serverName;
    private final Supplier<McpSyncClient> clientFactory;
    private final AtomicReference<SyncMcpToolCallbackProvider> delegateRef;

    public SessionRecoveringToolCallbackProvider(String serverName, Supplier<McpSyncClient> clientFactory) {
        this.serverName = serverName;
        this.clientFactory = clientFactory;
        this.delegateRef = new AtomicReference<>(createDelegate());
    }

    private SyncMcpToolCallbackProvider createDelegate() {
        try {
            logger.debug("Creating new MCP client for server: {}", serverName);
            McpSyncClient client = clientFactory.get();
            client.initialize();
            return new SyncMcpToolCallbackProvider(client);
        } catch (Exception e) {
            logger.error("Failed to create MCP client for {}: {}", serverName, e.getMessage(), e);
            throw new RuntimeException("Failed to create MCP client for " + serverName, e);
        }
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        SyncMcpToolCallbackProvider delegate = delegateRef.get();
        ToolCallback[] originalCallbacks = delegate.getToolCallbacks();

        return Arrays.stream(originalCallbacks)
                .map(this::wrapToolCallback)
                .toArray(ToolCallback[]::new);
    }

    private ToolCallback wrapToolCallback(ToolCallback originalCallback) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return originalCallback.getToolDefinition();
            }

            @Override
            public String call(String functionArguments) {
                try {
                    return originalCallback.call(functionArguments);
                } catch (Exception e) {
                    if (isSessionError(e)) {
                        logger.warn("Session error detected for MCP server {}: {}. Attempting recovery...",
                                serverName, e.getMessage());

                        try {
                            SyncMcpToolCallbackProvider newDelegate = createDelegate();
                            delegateRef.set(newDelegate);

                            logger.info("Successfully reconnected to MCP server: {}. Retrying tool invocation...",
                                    serverName);

                            ToolDefinition toolDef = originalCallback.getToolDefinition();
                            ToolCallback newCallback = Arrays.stream(newDelegate.getToolCallbacks())
                                    .filter(cb -> cb.getToolDefinition().equals(toolDef))
                                    .findFirst()
                                    .orElseThrow(() -> new RuntimeException("Tool not found after reconnection"));

                            return newCallback.call(functionArguments);
                        } catch (Exception reconnectError) {
                            logger.error("Failed to recover from session error for MCP server {}: {}",
                                    serverName, reconnectError.getMessage(), reconnectError);
                            throw new RuntimeException("Session recovery failed for " + serverName, reconnectError);
                        }
                    }

                    throw e;
                }
            }
        };
    }

    private boolean isSessionError(Exception e) {
        Throwable current = e;
        while (current != null) {
            String className = current.getClass().getName();
            if (className.contains("McpTransportSessionNotFoundException") ||
                className.contains("SessionNotFoundException")) {
                return true;
            }

            String currentMessage = current.getMessage();
            if (currentMessage != null) {
                if (currentMessage.contains("Session not found") ||
                    currentMessage.contains("session not found") ||
                    currentMessage.contains("Invalid session ID") ||
                    currentMessage.contains("invalid session") ||
                    currentMessage.contains("MCP session with server terminated") ||
                    currentMessage.contains("session with server terminated")) {
                    return true;
                }

                if (currentMessage.contains("404")) {
                    return true;
                }

                if (currentMessage.contains("400") && currentMessage.toLowerCase().contains("session")) {
                    return true;
                }

                if (currentMessage.contains("-32602") && currentMessage.toLowerCase().contains("session")) {
                    return true;
                }
            }

            current = current.getCause();
        }

        return false;
    }
}
