package com.example.cfchat.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class McpServerService {
    private static final Logger logger = LoggerFactory.getLogger(McpServerService.class);

    private final String name;
    private final String serverUrl;
    private final ProtocolType protocol;
    private final Map<String, String> headers;
    private final McpClientFactory clientFactory;
    private final AtomicReference<Map<String, String>> additionalHeaders = new AtomicReference<>(Map.of());
    private final AtomicReference<String> displayName = new AtomicReference<>(null);

    public McpServerService(String name, String serverUrl, ProtocolType protocol, McpClientFactory clientFactory) {
        this(name, serverUrl, protocol, Map.of(), clientFactory);
    }

    public McpServerService(String name, String serverUrl, ProtocolType protocol, Map<String, String> headers, McpClientFactory clientFactory) {
        this.name = name;
        this.serverUrl = serverUrl;
        this.protocol = protocol;
        this.headers = headers != null ? Map.copyOf(headers) : Map.of();
        this.clientFactory = clientFactory;
    }

    public McpSyncClient createMcpSyncClient() {
        Map<String, String> effectiveHeaders = getEffectiveHeaders();
        return switch (protocol) {
            case ProtocolType.StreamableHttp streamableHttp ->
                    clientFactory.createStreamableClient(serverUrl, Duration.ofSeconds(30), Duration.ofMinutes(5), effectiveHeaders);
            case ProtocolType.SSE sse ->
                    clientFactory.createSseClient(serverUrl, Duration.ofSeconds(30), Duration.ofMinutes(5), effectiveHeaders);
            case ProtocolType.Legacy legacy ->
                    clientFactory.createSseClient(serverUrl, Duration.ofSeconds(30), Duration.ofMinutes(5), effectiveHeaders);
        };
    }

    public McpSyncClient createHealthCheckClient() {
        return clientFactory.createHealthCheckClient(serverUrl, protocol, getEffectiveHeaders());
    }

    public void setAdditionalHeaders(Map<String, String> headers) {
        this.additionalHeaders.set(headers != null ? Map.copyOf(headers) : Map.of());
        logger.info("Additional headers updated for MCP server {} ({} header(s))",
                name, this.additionalHeaders.get().size());
    }

    public Map<String, String> getEffectiveHeaders() {
        Map<String, String> additional = additionalHeaders.get();
        if (additional.isEmpty()) {
            return headers;
        }
        Map<String, String> merged = new HashMap<>(headers);
        merged.putAll(additional);
        return Map.copyOf(merged);
    }

    public boolean hasAdditionalHeaders() {
        return !additionalHeaders.get().isEmpty();
    }

    public McpServerInfo getHealthyMcpServer() {
        try (McpSyncClient client = createHealthCheckClient()) {
            McpSchema.InitializeResult initResult = client.initialize();
            logger.debug("Initialized MCP server {}: protocol version {}", name, initResult.protocolVersion());

            String serverName = initResult.serverInfo() != null
                    ? initResult.serverInfo().name()
                    : name;

            McpSchema.ListToolsResult toolsResult = client.listTools();

            List<ToolInfo> tools = toolsResult.tools().stream()
                    .map(tool -> new ToolInfo(tool.name(), tool.description()))
                    .collect(Collectors.toList());

            logger.info("MCP server {} is healthy with {} tools ({})",
                    serverName, tools.size(), protocol.displayName());

            return new McpServerInfo(name, serverName, true, tools, protocol);

        } catch (Exception e) {
            logger.warn("Health check failed for MCP server {} ({}): {}",
                    name, protocol.displayName(), e.getMessage());
            return new McpServerInfo(name, name, false, Collections.emptyList(), protocol);
        }
    }

    public String getName() {
        return name;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public ProtocolType getProtocol() {
        return protocol;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getDisplayName() {
        String custom = displayName.get();
        return custom != null ? custom : name;
    }

    public void setDisplayName(String displayName) {
        this.displayName.set(displayName != null && !displayName.isBlank() ? displayName.trim() : null);
        logger.info("Display name for MCP server {} set to: {}", name, this.displayName.get());
    }

    public boolean hasCustomDisplayName() {
        return displayName.get() != null;
    }

    public record ToolInfo(String name, String description) {}

    public record McpServerInfo(
            String bindingName,
            String serverName,
            boolean healthy,
            List<ToolInfo> tools,
            ProtocolType protocol
    ) {}
}
