package com.example.cfchat.service;

import com.example.cfchat.mcp.McpClientFactory;
import com.example.cfchat.model.McpServer;
import com.example.cfchat.model.McpTransportType;
import com.example.cfchat.model.Tool;
import com.example.cfchat.model.ToolType;
import com.example.cfchat.repository.McpServerRepository;
import com.example.cfchat.repository.ToolRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class McpService {

    private final McpServerRepository mcpServerRepository;
    private final ToolRepository toolRepository;
    private final ObjectMapper objectMapper;
    private final McpClientFactory mcpClientFactory;
    private final JdbcTemplate jdbcTemplate;

    private final Map<UUID, McpConnectionState> connectionStates = new ConcurrentHashMap<>();
    private final Map<UUID, McpSyncClient> activeClients = new ConcurrentHashMap<>();

    public McpService(McpServerRepository mcpServerRepository,
                      ToolRepository toolRepository,
                      ObjectMapper objectMapper,
                      McpClientFactory mcpClientFactory,
                      JdbcTemplate jdbcTemplate) {
        this.mcpServerRepository = mcpServerRepository;
        this.toolRepository = toolRepository;
        this.objectMapper = objectMapper;
        this.mcpClientFactory = mcpClientFactory;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down MCP service - disconnecting {} active clients", activeClients.size());
        for (UUID serverId : new ArrayList<>(activeClients.keySet())) {
            try {
                disconnect(serverId);
            } catch (Exception e) {
                log.warn("Error disconnecting MCP server {} during shutdown: {}", serverId, e.getMessage());
            }
        }
    }

    @PostConstruct
    public void migrateTransportTypeConstraint() {
        try {
            jdbcTemplate.execute("ALTER TABLE mcp_servers DROP CONSTRAINT IF EXISTS mcp_servers_transport_type_check");
            jdbcTemplate.execute("ALTER TABLE mcp_servers ADD CONSTRAINT mcp_servers_transport_type_check " +
                    "CHECK (transport_type IN ('SSE', 'STDIO', 'STREAMABLE_HTTP'))");
            log.info("Updated mcp_servers transport_type check constraint to include STREAMABLE_HTTP");
        } catch (Exception e) {
            log.debug("Transport type constraint migration skipped: {}", e.getMessage());
        }
    }

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
        if (updates.getHeaders() != null) {
            server.setHeaders(updates.getHeaders());
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

        // Disconnect existing connection if any
        if (activeClients.containsKey(serverId)) {
            disconnect(serverId);
        }

        connectionStates.put(serverId, new McpConnectionState(ConnectionStatus.CONNECTING, null, 0));

        try {
            log.info("Connecting to MCP server: {} ({})", server.getName(), server.getTransportType());

            if (server.getTransportType() == McpTransportType.STDIO) {
                throw new IllegalStateException("STDIO transport is not supported for admin-configured MCP servers");
            }

            String url = server.getUrl();
            if (url == null || url.isBlank()) {
                throw new IllegalArgumentException("URL is required for " + server.getTransportType() + " transport");
            }
            validateMcpUrl(url);

            // Parse headers from the server configuration
            Map<String, String> headers = parseHeaders(server.getHeaders());
            log.info("{} connection to: {} with {} header(s)", server.getTransportType(), url, headers.size());

            // Create the MCP client based on transport type
            McpSyncClient client;
            if (server.getTransportType() == McpTransportType.STREAMABLE_HTTP) {
                client = mcpClientFactory.createStreamableClient(
                    url, Duration.ofSeconds(30), Duration.ofMinutes(5), headers);
            } else {
                client = mcpClientFactory.createSseClient(
                    url, Duration.ofSeconds(30), Duration.ofMinutes(5), headers);
            }

            // Initialize the connection
            McpSchema.InitializeResult initResult = client.initialize();
            String serverName = initResult.serverInfo() != null
                ? initResult.serverInfo().name()
                : server.getName();
            log.info("Initialized MCP server {}: protocol version {}", serverName, initResult.protocolVersion());

            // Store the active client
            activeClients.put(serverId, client);

            // Get initial tool count
            McpSchema.ListToolsResult toolsResult = client.listTools();
            int toolCount = toolsResult.tools() != null ? toolsResult.tools().size() : 0;

            McpConnectionState state = new McpConnectionState(ConnectionStatus.CONNECTED, null, toolCount);
            connectionStates.put(serverId, state);

            log.info("Successfully connected to MCP server {} with {} tools available", server.getName(), toolCount);
            return state;

        } catch (Exception e) {
            log.error("Failed to connect to MCP server {}: {}", server.getName(), e.getMessage(), e);
            McpConnectionState state = new McpConnectionState(ConnectionStatus.ERROR, e.getMessage(), 0);
            connectionStates.put(serverId, state);
            return state;
        }
    }

    public McpConnectionState disconnect(UUID serverId) {
        log.info("Disconnecting MCP server: {}", serverId);

        McpSyncClient client = activeClients.remove(serverId);
        if (client != null) {
            try {
                client.close();
                log.info("Closed MCP client for server: {}", serverId);
            } catch (Exception e) {
                log.warn("Error closing MCP client for server {}: {}", serverId, e.getMessage());
            }
        }

        McpConnectionState state = new McpConnectionState(ConnectionStatus.DISCONNECTED, null, 0);
        connectionStates.put(serverId, state);
        return state;
    }

    public McpConnectionState getConnectionState(UUID serverId) {
        return connectionStates.getOrDefault(serverId,
            new McpConnectionState(ConnectionStatus.DISCONNECTED, null, 0));
    }

    /**
     * Get the active MCP client for a server, if connected.
     */
    public Optional<McpSyncClient> getActiveClient(UUID serverId) {
        return Optional.ofNullable(activeClients.get(serverId));
    }

    @Transactional
    public List<Tool> syncTools(UUID serverId) {
        McpServer server = mcpServerRepository.findById(serverId)
            .orElseThrow(() -> new IllegalArgumentException("MCP server not found: " + serverId));

        McpSyncClient client = activeClients.get(serverId);
        if (client == null) {
            throw new IllegalStateException("MCP server is not connected");
        }

        log.info("Syncing tools from MCP server: {}", server.getName());

        try {
            // Fetch tools from the MCP server
            McpSchema.ListToolsResult toolsResult = client.listTools();
            List<McpSchema.Tool> mcpTools = toolsResult.tools();

            if (mcpTools == null || mcpTools.isEmpty()) {
                log.info("No tools available from MCP server: {}", server.getName());
                // Remove any existing tools for this server
                toolRepository.deleteByMcpServerId(serverId);
                connectionStates.put(serverId, new McpConnectionState(ConnectionStatus.CONNECTED, null, 0));
                return List.of();
            }

            log.info("Found {} tools from MCP server: {}", mcpTools.size(), server.getName());

            // Delete existing tools for this server and recreate
            toolRepository.deleteByMcpServerId(serverId);
            toolRepository.flush();

            List<Tool> savedTools = new ArrayList<>();
            for (McpSchema.Tool mcpTool : mcpTools) {
                String inputSchema = null;
                if (mcpTool.inputSchema() != null) {
                    try {
                        inputSchema = objectMapper.writeValueAsString(mcpTool.inputSchema());
                    } catch (JsonProcessingException e) {
                        log.warn("Failed to serialize input schema for tool {}: {}", mcpTool.name(), e.getMessage());
                    }
                }

                Tool tool = addToolFromMcp(
                    serverId,
                    mcpTool.name(),
                    mcpTool.name(),
                    mcpTool.description(),
                    inputSchema
                );
                savedTools.add(tool);
                log.debug("Synced tool: {} - {}", mcpTool.name(), mcpTool.description());
            }

            // Update connection state with tool count
            connectionStates.put(serverId, new McpConnectionState(
                ConnectionStatus.CONNECTED, null, savedTools.size()));

            return savedTools;

        } catch (Exception e) {
            log.error("Failed to sync tools from MCP server {}: {}", server.getName(), e.getMessage(), e);
            throw new IllegalStateException("Failed to sync tools: " + e.getMessage(), e);
        }
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

        if (server.getTransportType() == McpTransportType.STDIO) {
            log.warn("STDIO transport is not supported for connection testing");
            return false;
        }

        String url = server.getUrl();
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            validateMcpUrl(url);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid MCP URL for server {}: {}", server.getName(), e.getMessage());
            return false;
        }

        Map<String, String> headers = parseHeaders(server.getHeaders());

        McpSyncClient testClient;
        if (server.getTransportType() == McpTransportType.STREAMABLE_HTTP) {
            testClient = mcpClientFactory.createStreamableClient(
                    url, Duration.ofSeconds(10), Duration.ofSeconds(10), headers);
        } else {
            testClient = mcpClientFactory.createSseClient(
                    url, Duration.ofSeconds(10), Duration.ofSeconds(10), headers);
        }

        try (testClient) {

            McpSchema.InitializeResult result = testClient.initialize();
            log.info("Test connection successful to {}: protocol version {}",
                server.getName(), result.protocolVersion());
            return true;

        } catch (Exception e) {
            log.warn("Test connection failed for {}: {}", server.getName(), e.getMessage());
            return false;
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

    public Map<String, String> parseHeaders(String headersJson) {
        if (headersJson == null || headersJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(headersJson, new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse headers JSON: {}", e.getMessage());
            return Map.of();
        }
    }

    private void validateMcpUrl(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("MCP URL must use http or https scheme");
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("MCP URL must have a valid host");
            }
            // Block loopback and link-local addresses (allow site-local for CF-internal services)
            InetAddress addr = InetAddress.getByName(host);
            if (addr.isLoopbackAddress() || addr.isLinkLocalAddress() || addr.isAnyLocalAddress()) {
                throw new IllegalArgumentException("MCP URL must not point to loopback or link-local addresses");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid MCP URL: " + e.getMessage());
        }
    }
}
