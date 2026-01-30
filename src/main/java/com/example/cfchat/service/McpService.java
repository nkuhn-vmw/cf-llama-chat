package com.example.cfchat.service;

import com.example.cfchat.model.McpServer;
import com.example.cfchat.model.McpTransportType;
import com.example.cfchat.model.Tool;
import com.example.cfchat.model.ToolType;
import com.example.cfchat.repository.McpServerRepository;
import com.example.cfchat.repository.ToolRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class McpService {

    private final McpServerRepository mcpServerRepository;
    private final ToolRepository toolRepository;
    private final ObjectMapper objectMapper;

    private final Map<UUID, McpConnectionState> connectionStates = new ConcurrentHashMap<>();

    public enum ConnectionStatus {
        CONNECTED,
        DISCONNECTED,
        CONNECTING,
        ERROR
    }

    public record McpConnectionState(
        ConnectionStatus status,
        String errorMessage,
        int toolCount
    ) {}

    public List<McpServer> getAllServers() {
        return mcpServerRepository.findAll();
    }

    public List<McpServer> getEnabledServers() {
        return mcpServerRepository.findByEnabled(true);
    }

    public Optional<McpServer> getServerById(UUID id) {
        return mcpServerRepository.findById(id);
    }

    @Transactional
    public McpServer createServer(McpServer server) {
        if (mcpServerRepository.existsByName(server.getName())) {
            throw new IllegalArgumentException("MCP server with name '" + server.getName() + "' already exists");
        }
        McpServer saved = mcpServerRepository.save(server);
        connectionStates.put(saved.getId(), new McpConnectionState(ConnectionStatus.DISCONNECTED, null, 0));
        return saved;
    }

    @Transactional
    public McpServer updateServer(UUID id, McpServer updates) {
        McpServer server = mcpServerRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("MCP server not found: " + id));

        if (updates.getName() != null && !updates.getName().equals(server.getName())) {
            if (mcpServerRepository.existsByName(updates.getName())) {
                throw new IllegalArgumentException("MCP server with name '" + updates.getName() + "' already exists");
            }
            server.setName(updates.getName());
        }

        if (updates.getDescription() != null) {
            server.setDescription(updates.getDescription());
        }
        if (updates.getTransportType() != null) {
            server.setTransportType(updates.getTransportType());
        }
        if (updates.getUrl() != null) {
            server.setUrl(updates.getUrl());
        }
        if (updates.getCommand() != null) {
            server.setCommand(updates.getCommand());
        }
        if (updates.getArgs() != null) {
            server.setArgs(updates.getArgs());
        }
        if (updates.getEnvVars() != null) {
            server.setEnvVars(updates.getEnvVars());
        }
        if (updates.getOauthConfig() != null) {
            server.setOauthConfig(updates.getOauthConfig());
        }

        return mcpServerRepository.save(server);
    }

    @Transactional
    public void deleteServer(UUID id) {
        if (!mcpServerRepository.existsById(id)) {
            throw new IllegalArgumentException("MCP server not found: " + id);
        }
        disconnect(id);
        toolRepository.deleteByMcpServerId(id);
        mcpServerRepository.deleteById(id);
        connectionStates.remove(id);
    }

    @Transactional
    public McpServer setEnabled(UUID id, boolean enabled) {
        McpServer server = mcpServerRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("MCP server not found: " + id));
        server.setEnabled(enabled);
        if (!enabled) {
            disconnect(id);
        }
        return mcpServerRepository.save(server);
    }

    public McpConnectionState connect(UUID serverId) {
        McpServer server = mcpServerRepository.findById(serverId)
            .orElseThrow(() -> new IllegalArgumentException("MCP server not found: " + serverId));

        connectionStates.put(serverId, new McpConnectionState(ConnectionStatus.CONNECTING, null, 0));

        try {
            // TODO: Implement actual MCP connection using Spring AI MCP Client
            // For now, simulate a successful connection
            log.info("Connecting to MCP server: {} ({})", server.getName(), server.getTransportType());

            if (server.getTransportType() == McpTransportType.SSE) {
                // SSE connection would use server.getUrl()
                log.info("SSE connection to: {}", server.getUrl());
            } else {
                // STDIO connection would use server.getCommand() and server.getArgs()
                log.info("STDIO connection: {} {}", server.getCommand(), server.getArgs());
            }

            // Simulate connection success
            int toolCount = toolRepository.findByMcpServerId(serverId).size();
            McpConnectionState state = new McpConnectionState(ConnectionStatus.CONNECTED, null, toolCount);
            connectionStates.put(serverId, state);
            return state;

        } catch (Exception e) {
            log.error("Failed to connect to MCP server {}: {}", server.getName(), e.getMessage());
            McpConnectionState state = new McpConnectionState(ConnectionStatus.ERROR, e.getMessage(), 0);
            connectionStates.put(serverId, state);
            return state;
        }
    }

    public McpConnectionState disconnect(UUID serverId) {
        log.info("Disconnecting MCP server: {}", serverId);

        // TODO: Implement actual MCP disconnection
        McpConnectionState state = new McpConnectionState(ConnectionStatus.DISCONNECTED, null, 0);
        connectionStates.put(serverId, state);
        return state;
    }

    public McpConnectionState getConnectionState(UUID serverId) {
        return connectionStates.getOrDefault(serverId,
            new McpConnectionState(ConnectionStatus.DISCONNECTED, null, 0));
    }

    @Transactional
    public List<Tool> syncTools(UUID serverId) {
        McpServer server = mcpServerRepository.findById(serverId)
            .orElseThrow(() -> new IllegalArgumentException("MCP server not found: " + serverId));

        McpConnectionState state = connectionStates.get(serverId);
        if (state == null || state.status() != ConnectionStatus.CONNECTED) {
            throw new IllegalStateException("MCP server is not connected");
        }

        log.info("Syncing tools from MCP server: {}", server.getName());

        // TODO: Implement actual tool discovery using MCP protocol
        // For now, return existing tools or create placeholder tools for testing

        List<Tool> existingTools = toolRepository.findByMcpServerId(serverId);

        // If no tools exist yet, we would normally fetch from the MCP server
        // The actual implementation would call the MCP server's list_tools endpoint

        // Update connection state with tool count
        connectionStates.put(serverId, new McpConnectionState(
            ConnectionStatus.CONNECTED, null, existingTools.size()));

        return existingTools;
    }

    @Transactional
    public Tool addToolFromMcp(UUID serverId, String name, String displayName, String description, String inputSchema) {
        if (!mcpServerRepository.existsById(serverId)) {
            throw new IllegalArgumentException("MCP server not found: " + serverId);
        }

        String uniqueName = serverId.toString().substring(0, 8) + "_" + name;

        Tool tool = Tool.builder()
            .name(uniqueName)
            .displayName(displayName != null ? displayName : name)
            .description(description)
            .type(ToolType.MCP)
            .mcpServerId(serverId)
            .inputSchema(inputSchema)
            .enabled(true)
            .build();

        return toolRepository.save(tool);
    }

    public boolean testConnection(UUID serverId) {
        McpServer server = mcpServerRepository.findById(serverId)
            .orElseThrow(() -> new IllegalArgumentException("MCP server not found: " + serverId));

        log.info("Testing connection to MCP server: {}", server.getName());

        // TODO: Implement actual connection test
        // For now, just check if the server configuration is valid
        if (server.getTransportType() == McpTransportType.SSE) {
            return server.getUrl() != null && !server.getUrl().isBlank();
        } else {
            return server.getCommand() != null && !server.getCommand().isBlank();
        }
    }

    public List<String> parseArgs(String argsJson) {
        if (argsJson == null || argsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(argsJson, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse args JSON: {}", e.getMessage());
            return List.of();
        }
    }

    public Map<String, String> parseEnvVars(String envVarsJson) {
        if (envVarsJson == null || envVarsJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(envVarsJson, new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse env vars JSON: {}", e.getMessage());
            return Map.of();
        }
    }
}
