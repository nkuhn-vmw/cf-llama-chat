package com.example.cfchat.controller;

import com.example.cfchat.auth.UserService;
import com.example.cfchat.mcp.McpServerService;
import com.example.cfchat.mcp.McpToolCallbackCacheService;
import com.example.cfchat.model.McpServer;
import com.example.cfchat.model.McpTransportType;
import com.example.cfchat.model.Tool;
import com.example.cfchat.model.User;
import com.example.cfchat.service.McpService;
import com.example.cfchat.service.ToolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
@Slf4j
@RequiredArgsConstructor
public class AdminMcpController {

    private final UserService userService;
    private final McpService mcpService;
    private final ToolService toolService;
    private final McpToolCallbackCacheService mcpToolCallbackCacheService;

    @GetMapping("/admin/mcp")
    public String mcpPage(Model model) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return "redirect:/";
        }

        List<McpServer> servers = mcpService.getAllServers();
        List<Map<String, Object>> serverDataList = new ArrayList<>();

        for (McpServer server : servers) {
            Map<String, Object> serverData = new HashMap<>();
            serverData.put("server", server);
            serverData.put("connectionState", mcpService.getConnectionState(server.getId()));
            serverData.put("toolCount", toolService.getToolsByMcpServer(server.getId()).size());
            serverDataList.add(serverData);
        }

        // CF service binding MCP servers (auto-discovered, with health check)
        List<Map<String, Object>> bindingDataList = new ArrayList<>();
        for (var service : mcpToolCallbackCacheService.getMcpServerServices()) {
            McpServerService.McpServerInfo info = service.getHealthyMcpServer();
            Map<String, Object> data = new HashMap<>();
            data.put("name", service.getName());
            data.put("url", service.getServerUrl());
            data.put("protocol", service.getProtocol().displayName());
            data.put("healthy", info.healthy());
            data.put("serverName", info.serverName());
            data.put("toolCount", info.tools().size());
            data.put("tools", info.tools());
            data.put("hasToken", service.hasAdditionalHeaders());
            data.put("displayName", service.getDisplayName());
            data.put("hasCustomDisplayName", service.hasCustomDisplayName());
            bindingDataList.add(data);
        }

        model.addAttribute("servers", serverDataList);
        model.addAttribute("bindingServers", bindingDataList);
        model.addAttribute("totalServers", servers.size() + bindingDataList.size());
        model.addAttribute("enabledServers", servers.stream().filter(McpServer::isEnabled).count() + bindingDataList.size());

        return "admin/mcp";
    }

    @GetMapping("/api/admin/mcp/servers")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getServers() {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).build();
        }

        List<McpServer> servers = mcpService.getAllServers();
        List<Map<String, Object>> result = new ArrayList<>();

        for (McpServer server : servers) {
            Map<String, Object> serverData = new HashMap<>();
            serverData.put("id", server.getId());
            serverData.put("name", server.getName());
            serverData.put("description", server.getDescription());
            serverData.put("transportType", server.getTransportType().name());
            serverData.put("url", server.getUrl());
            serverData.put("command", server.getCommand());
            serverData.put("args", server.getArgs());
            serverData.put("envVars", server.getEnvVars() != null && !server.getEnvVars().isBlank() ? "****" : null);
            serverData.put("headers", server.getHeaders() != null && !server.getHeaders().isBlank() ? "****" : null);
            serverData.put("hasEnvVars", server.getEnvVars() != null && !server.getEnvVars().isBlank());
            serverData.put("hasHeaders", server.getHeaders() != null && !server.getHeaders().isBlank());
            serverData.put("enabled", server.isEnabled());
            serverData.put("requiresAuth", server.isRequiresAuth());
            serverData.put("createdAt", server.getCreatedAt());
            serverData.put("updatedAt", server.getUpdatedAt());

            McpService.McpConnectionState state = mcpService.getConnectionState(server.getId());
            serverData.put("connectionStatus", state.status().name());
            serverData.put("connectionError", state.errorMessage());
            serverData.put("toolCount", state.toolCount());

            result.add(serverData);
        }

        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/admin/mcp/servers")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createServer(@RequestBody Map<String, Object> body) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        try {
            String name = (String) body.get("name");
            String description = (String) body.get("description");
            String transportTypeStr = (String) body.get("transportType");
            String url = (String) body.get("url");
            String command = (String) body.get("command");
            String args = (String) body.get("args");
            String envVars = (String) body.get("envVars");
            String headers = (String) body.get("headers");
            Boolean requiresAuth = (Boolean) body.getOrDefault("requiresAuth", false);
            String oauthConfig = (String) body.get("oauthConfig");

            if (name == null || name.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Name is required"));
            }

            McpTransportType transportType = McpTransportType.valueOf(transportTypeStr);

            if ((transportType == McpTransportType.SSE || transportType == McpTransportType.STREAMABLE_HTTP)
                    && (url == null || url.isBlank())) {
                return ResponseEntity.badRequest().body(Map.of("error", "URL is required for " + transportType + " transport"));
            }

            if (transportType == McpTransportType.STDIO && (command == null || command.isBlank())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Command is required for STDIO transport"));
            }

            McpServer server = McpServer.builder()
                .name(name)
                .description(description)
                .transportType(transportType)
                .url(url)
                .command(command)
                .args(args)
                .envVars(envVars)
                .headers(headers)
                .requiresAuth(requiresAuth != null && requiresAuth)
                .oauthConfig(oauthConfig)
                .enabled(true)
                .build();

            McpServer saved = mcpService.createServer(server);
            log.info("Admin {} created MCP server: {}", currentUser.get().getUsername(), name);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "serverId", saved.getId(),
                "name", saved.getName()
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/api/admin/mcp/servers/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateServer(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {

        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        try {
            McpServer updates = McpServer.builder()
                .name((String) body.get("name"))
                .description((String) body.get("description"))
                .url((String) body.get("url"))
                .command((String) body.get("command"))
                .args((String) body.get("args"))
                .envVars((String) body.get("envVars"))
                .headers((String) body.get("headers"))
                .oauthConfig((String) body.get("oauthConfig"))
                .build();

            String transportTypeStr = (String) body.get("transportType");
            if (transportTypeStr != null) {
                updates.setTransportType(McpTransportType.valueOf(transportTypeStr));
            }

            Boolean requiresAuth = (Boolean) body.get("requiresAuth");
            if (requiresAuth != null) {
                updates.setRequiresAuth(requiresAuth);
            }

            McpServer updated = mcpService.updateServer(id, updates);
            log.info("Admin {} updated MCP server: {}", currentUser.get().getUsername(), updated.getName());

            return ResponseEntity.ok(Map.of("success", true, "serverId", updated.getId()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/api/admin/mcp/servers/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteServer(@PathVariable UUID id) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        try {
            mcpService.deleteServer(id);
            log.info("Admin {} deleted MCP server: {}", currentUser.get().getUsername(), id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/api/admin/mcp/servers/{id}/enabled")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> setServerEnabled(
            @PathVariable UUID id,
            @RequestBody Map<String, Boolean> body) {

        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        Boolean enabled = body.get("enabled");
        if (enabled == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "enabled field is required"));
        }

        try {
            McpServer server = mcpService.setEnabled(id, enabled);
            log.info("Admin {} set MCP server {} enabled: {}",
                currentUser.get().getUsername(), server.getName(), enabled);

            return ResponseEntity.ok(Map.of("success", true, "enabled", server.isEnabled()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/admin/mcp/servers/{id}/connect")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> connectServer(@PathVariable UUID id) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        try {
            McpService.McpConnectionState state = mcpService.connect(id);
            log.info("Admin {} connected MCP server: {}", currentUser.get().getUsername(), id);
            mcpToolCallbackCacheService.invalidateCache();

            return ResponseEntity.ok(Map.of(
                "success", state.status() == McpService.ConnectionStatus.CONNECTED,
                "status", state.status().name(),
                "error", state.errorMessage() != null ? state.errorMessage() : "",
                "toolCount", state.toolCount()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/admin/mcp/servers/{id}/disconnect")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> disconnectServer(@PathVariable UUID id) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        try {
            McpService.McpConnectionState state = mcpService.disconnect(id);
            log.info("Admin {} disconnected MCP server: {}", currentUser.get().getUsername(), id);
            mcpToolCallbackCacheService.invalidateCache();

            return ResponseEntity.ok(Map.of(
                "success", true,
                "status", state.status().name()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/admin/mcp/servers/{id}/sync-tools")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncTools(@PathVariable UUID id) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        try {
            List<Tool> tools = mcpService.syncTools(id);
            log.info("Admin {} synced tools from MCP server: {}, found {} tools",
                currentUser.get().getUsername(), id, tools.size());
            mcpToolCallbackCacheService.invalidateCache();

            return ResponseEntity.ok(Map.of(
                "success", true,
                "toolCount", tools.size()
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/admin/mcp/servers/{id}/test")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testConnection(@PathVariable UUID id) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        try {
            boolean success = mcpService.testConnection(id);
            return ResponseEntity.ok(Map.of("success", success));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/admin/mcp/bindings/{name}/display-name")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> setBindingDisplayName(
            @PathVariable String name,
            @RequestBody Map<String, String> body) {

        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        Optional<McpServerService> service = mcpToolCallbackCacheService.getMcpServerServices().stream()
                .filter(s -> s.getName().equals(name))
                .findFirst();

        if (service.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Service binding not found: " + name));
        }

        String displayName = body.get("displayName");
        service.get().setDisplayName(displayName);
        log.info("Admin {} set display name for binding {}: {}", currentUser.get().getUsername(), name, displayName);

        return ResponseEntity.ok(Map.of("success", true, "displayName", service.get().getDisplayName()));
    }

    @PostMapping("/api/admin/mcp/bindings/{name}/token")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> setBindingToken(
            @PathVariable String name,
            @RequestBody Map<String, String> body) {

        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        String token = body.get("token");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Token is required"));
        }

        Optional<McpServerService> service = mcpToolCallbackCacheService.getMcpServerServices().stream()
                .filter(s -> s.getName().equals(name))
                .findFirst();

        if (service.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Service binding not found: " + name));
        }

        service.get().setAdditionalHeaders(Map.of("Authorization", "Bearer " + token.trim()));
        mcpToolCallbackCacheService.invalidateCache();
        log.info("Admin {} set auth token for binding: {}", currentUser.get().getUsername(), name);

        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/api/admin/mcp/bindings/{name}/probe")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> probeBindingServer(@PathVariable String name) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        Optional<McpServerService> service = mcpToolCallbackCacheService.getMcpServerServices().stream()
                .filter(s -> s.getName().equals(name))
                .findFirst();

        if (service.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Service binding not found: " + name));
        }

        McpServerService.McpServerInfo info = service.get().getHealthyMcpServer();
        Map<String, Object> result = new HashMap<>();
        result.put("healthy", info.healthy());
        result.put("serverName", info.serverName());
        result.put("toolCount", info.tools().size());
        result.put("tools", info.tools().stream()
                .map(t -> Map.of("name", t.name(), "description", t.description() != null ? t.description() : ""))
                .toList());

        return ResponseEntity.ok(result);
    }
}
