package com.example.cfchat.controller;

import com.example.cfchat.auth.UserService;
import com.example.cfchat.mcp.McpServerService;
import com.example.cfchat.mcp.McpToolCallbackCacheService;
import com.example.cfchat.model.McpServer;
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
public class AdminToolsController {

    private final UserService userService;
    private final ToolService toolService;
    private final McpService mcpService;
    private final McpToolCallbackCacheService mcpToolCallbackCacheService;

    /**
     * Public endpoint to check if any MCP tools are available for the current user.
     * This is used by the frontend to show/hide the "Use Tools" toggle.
     */
    @GetMapping("/api/admin/tools/available")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkToolsAvailable() {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty()) {
            return ResponseEntity.ok(Map.of("available", false, "count", 0));
        }

        // Get tools accessible to the user (respects user access permissions)
        List<Tool> tools = toolService.getAccessibleTools(currentUser.get().getId());
        long enabledCount = tools.stream().filter(Tool::isEnabled).count();

        // Include auto-discovered binding server tools
        long bindingToolCount = mcpToolCallbackCacheService.getMcpServerServices().stream()
                .map(McpServerService::getCachedHealthCheck)
                .filter(info -> info != null && info.healthy())
                .mapToLong(info -> info.tools().size())
                .sum();

        long totalEnabled = enabledCount + bindingToolCount;

        return ResponseEntity.ok(Map.of(
                "available", totalEnabled > 0,
                "count", totalEnabled
        ));
    }

    @GetMapping("/admin/tools")
    public String toolsPage(Model model) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return "redirect:/";
        }

        List<Tool> tools = toolService.getAllTools();
        List<Map<String, Object>> toolDataList = new ArrayList<>();

        for (Tool tool : tools) {
            Map<String, Object> toolData = new HashMap<>();
            toolData.put("tool", tool);

            if (tool.getMcpServerId() != null) {
                mcpService.getServerById(tool.getMcpServerId())
                    .ifPresent(server -> toolData.put("mcpServerName", server.getName()));
            }

            toolDataList.add(toolData);
        }

        // Include auto-discovered binding server tools
        int bindingToolCount = 0;
        for (McpServerService service : mcpToolCallbackCacheService.getMcpServerServices()) {
            McpServerService.McpServerInfo healthCheck = service.getCachedHealthCheck();
            if (healthCheck == null) {
                healthCheck = service.getHealthyMcpServer();
            }
            if (healthCheck.healthy()) {
                for (McpServerService.ToolInfo toolInfo : healthCheck.tools()) {
                    Map<String, Object> toolData = new HashMap<>();
                    // Create a lightweight Tool-like object for the template
                    Tool bindingTool = Tool.builder()
                            .id(null)
                            .name(toolInfo.name())
                            .displayName(toolInfo.name())
                            .description(toolInfo.description())
                            .enabled(true)
                            .build();
                    toolData.put("tool", bindingTool);
                    toolData.put("mcpServerName", service.getDisplayName());
                    toolData.put("binding", true);
                    toolData.put("filterKey", "binding:" + service.getName());
                    toolDataList.add(toolData);
                    bindingToolCount++;
                }
            }
        }

        model.addAttribute("tools", toolDataList);
        model.addAttribute("totalTools", tools.size() + bindingToolCount);
        model.addAttribute("enabledTools", tools.stream().filter(Tool::isEnabled).count() + bindingToolCount);

        // Build unified server filter options (DB servers + binding servers)
        List<Map<String, String>> serverFilterOptions = new ArrayList<>();
        for (McpServer server : mcpService.getAllServers()) {
            serverFilterOptions.add(Map.of("value", server.getId().toString(), "label", server.getName()));
        }
        for (McpServerService service : mcpToolCallbackCacheService.getMcpServerServices()) {
            McpServerService.McpServerInfo info = service.getCachedHealthCheck();
            if (info != null && info.healthy()) {
                serverFilterOptions.add(Map.of("value", "binding:" + service.getName(), "label", service.getDisplayName() + " (binding)"));
            }
        }
        model.addAttribute("serverFilterOptions", serverFilterOptions);

        return "admin/tools";
    }

    @GetMapping("/api/admin/tools")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getTools(
            @RequestParam(required = false) UUID mcpServerId) {

        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).build();
        }

        List<Tool> tools;
        if (mcpServerId != null) {
            tools = toolService.getToolsByMcpServer(mcpServerId);
        } else {
            tools = toolService.getAllTools();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Tool tool : tools) {
            Map<String, Object> toolData = new HashMap<>();
            toolData.put("id", tool.getId());
            toolData.put("name", tool.getName());
            toolData.put("displayName", tool.getDisplayName());
            toolData.put("description", tool.getDescription());
            toolData.put("type", tool.getType().name());
            toolData.put("mcpServerId", tool.getMcpServerId());
            toolData.put("inputSchema", tool.getInputSchema());
            toolData.put("enabled", tool.isEnabled());
            toolData.put("createdAt", tool.getCreatedAt());

            if (tool.getMcpServerId() != null) {
                mcpService.getServerById(tool.getMcpServerId())
                    .ifPresent(server -> toolData.put("mcpServerName", server.getName()));
            }

            result.add(toolData);
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/admin/tools/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getTool(@PathVariable UUID id) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).build();
        }

        return toolService.getToolById(id)
            .map(tool -> {
                Map<String, Object> toolData = new HashMap<>();
                toolData.put("id", tool.getId());
                toolData.put("name", tool.getName());
                toolData.put("displayName", tool.getDisplayName());
                toolData.put("description", tool.getDescription());
                toolData.put("type", tool.getType().name());
                toolData.put("mcpServerId", tool.getMcpServerId());
                toolData.put("inputSchema", tool.getInputSchema());
                toolData.put("enabled", tool.isEnabled());
                toolData.put("createdAt", tool.getCreatedAt());

                if (tool.getMcpServerId() != null) {
                    mcpService.getServerById(tool.getMcpServerId())
                        .ifPresent(server -> toolData.put("mcpServerName", server.getName()));
                }

                return ResponseEntity.ok(toolData);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/api/admin/tools/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateTool(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {

        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        try {
            Tool updates = Tool.builder()
                .displayName((String) body.get("displayName"))
                .description((String) body.get("description"))
                .inputSchema((String) body.get("inputSchema"))
                .build();

            Tool updated = toolService.updateTool(id, updates);
            log.info("Admin {} updated tool: {}", currentUser.get().getUsername(), updated.getName());

            return ResponseEntity.ok(Map.of("success", true, "toolId", updated.getId()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/api/admin/tools/{id}/enabled")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> setToolEnabled(
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
            Tool tool = toolService.setEnabled(id, enabled);
            log.info("Admin {} set tool {} enabled: {}",
                currentUser.get().getUsername(), tool.getName(), enabled);

            return ResponseEntity.ok(Map.of("success", true, "enabled", tool.isEnabled()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/api/admin/tools/bulk-enabled")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> bulkSetEnabled(@RequestBody Map<String, Object> body) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        String mcpServerIdStr = (String) body.get("mcpServerId");
        Boolean enabled = (Boolean) body.get("enabled");

        if (mcpServerIdStr == null || enabled == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "mcpServerId and enabled fields are required"));
        }

        if (mcpServerIdStr.startsWith("binding:")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot bulk toggle binding server tools"));
        }

        try {
            UUID mcpServerId = UUID.fromString(mcpServerIdStr);
            int count = toolService.setEnabledByMcpServer(mcpServerId, enabled);
            log.info("Admin {} bulk set enabled={} for MCP server {}, affected {} tools",
                    currentUser.get().getUsername(), enabled, mcpServerId, count);
            return ResponseEntity.ok(Map.of("success", true, "count", count));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/api/admin/tools/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteTool(@PathVariable UUID id) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        try {
            toolService.deleteTool(id);
            log.info("Admin {} deleted tool: {}", currentUser.get().getUsername(), id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
