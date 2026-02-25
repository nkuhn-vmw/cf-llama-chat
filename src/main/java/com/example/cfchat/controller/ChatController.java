package com.example.cfchat.controller;

import com.example.cfchat.auth.UserService;
import com.example.cfchat.dto.ChatRequest;
import com.example.cfchat.dto.ChatResponse;
import com.example.cfchat.model.ModelInfo;
import com.example.cfchat.model.Skill;
import com.example.cfchat.mcp.McpServerService;
import com.example.cfchat.mcp.McpToolCallbackCacheService;
import com.example.cfchat.model.Tool;
import com.example.cfchat.model.User;
import com.example.cfchat.service.ChatService;
import com.example.cfchat.service.McpService;
import com.example.cfchat.service.SkillService;
import com.example.cfchat.service.ToolService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final UserService userService;
    private final ToolService toolService;
    private final SkillService skillService;
    private final McpService mcpService;
    private final McpToolCallbackCacheService mcpToolCallbackCacheService;

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        log.info("Received chat request for conversation: {}, useTools: {}", request.getConversationId(), request.isUseTools());
        ChatResponse response = chatService.chat(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatResponse> chatStream(@Valid @RequestBody ChatRequest request) {
        log.info("Received streaming chat request for conversation: {}, useTools: {}", request.getConversationId(), request.isUseTools());
        return chatService.chatStream(request);
    }

    @GetMapping("/models")
    public ResponseEntity<List<ModelInfo>> getModels() {
        return ResponseEntity.ok(chatService.getAvailableModels());
    }

    @GetMapping("/available-tools")
    public ResponseEntity<List<Map<String, Object>>> getAvailableTools() {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<Tool> tools = toolService.getAccessibleTools(currentUser.get().getId());
        List<Map<String, Object>> result = new ArrayList<>();

        for (Tool tool : tools) {
            Map<String, Object> toolData = new HashMap<>();
            toolData.put("id", tool.getId());
            toolData.put("name", tool.getName());
            toolData.put("displayName", tool.getDisplayName() != null ? tool.getDisplayName() : tool.getName());
            toolData.put("description", tool.getDescription());
            toolData.put("type", tool.getType().name());

            // Include MCP server name if this is an MCP tool
            if (tool.getMcpServerId() != null) {
                mcpService.getServerById(tool.getMcpServerId())
                    .ifPresent(server -> toolData.put("mcpServerName", server.getName()));
            }

            result.add(toolData);
        }

        // Include auto-discovered CF binding server tools
        for (McpServerService service : mcpToolCallbackCacheService.getMcpServerServices()) {
            Map<String, Object> toolData = new HashMap<>();
            toolData.put("id", "binding-" + service.getName());
            toolData.put("name", service.getName());
            toolData.put("displayName", service.getDisplayName());
            toolData.put("description", "Auto-discovered from CF service binding");
            toolData.put("type", "MCP_BINDING");
            toolData.put("mcpServerName", service.getDisplayName());
            result.add(toolData);
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/available-skills")
    public ResponseEntity<List<Map<String, Object>>> getAvailableSkills() {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<Skill> skills = skillService.getAccessibleSkills(currentUser.get().getId());
        List<Map<String, Object>> result = new ArrayList<>();

        for (Skill skill : skills) {
            Map<String, Object> skillData = new HashMap<>();
            skillData.put("id", skill.getId());
            skillData.put("name", skill.getName());
            skillData.put("displayName", skill.getDisplayName() != null ? skill.getDisplayName() : skill.getName());
            skillData.put("description", skill.getDescription());

            List<Tool> skillTools = skillService.getSkillTools(skill);
            skillData.put("toolCount", skillTools.size());

            result.add(skillData);
        }

        return ResponseEntity.ok(result);
    }
}
