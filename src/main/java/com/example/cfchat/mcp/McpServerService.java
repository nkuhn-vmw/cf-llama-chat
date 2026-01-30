package com.example.cfchat.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class McpServerService {
    private static final Logger logger = LoggerFactory.getLogger(McpServerService.class);

    private final String name;
    private final String serverUrl;
    private final ProtocolType protocol;
    private final Map<String, String> headers;
    private final McpClientFactory clientFactory;

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
        return switch (protocol) {
            case ProtocolType.StreamableHttp streamableHttp ->
                    clientFactory.createStreamableClient(serverUrl, Duration.ofSeconds(30), Duration.ofMinutes(5), headers);
            case ProtocolType.SSE sse ->
                    clientFactory.createSseClient(serverUrl, Duration.ofSeconds(30), Duration.ofMinutes(5), headers);
            case ProtocolType.Legacy legacy ->
                    clientFactory.createSseClient(serverUrl, Duration.ofSeconds(30), Duration.ofMinutes(5), headers);
        };
    }

    public McpSyncClient createHealthCheckClient() {
        return clientFactory.createHealthCheckClient(serverUrl, protocol, headers);
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

    public record ToolInfo(String name, String description) {}

    public record McpServerInfo(
            String bindingName,
            String serverName,
            boolean healthy,
            List<ToolInfo> tools,
            ProtocolType protocol
    ) {}
}
