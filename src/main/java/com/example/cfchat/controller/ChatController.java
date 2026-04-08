package com.example.cfchat.controller;

import com.example.cfchat.auth.UserService;
import com.example.cfchat.dto.ChatRequest;
import com.example.cfchat.dto.ChatResponse;
import com.example.cfchat.event.WikiOpEvent;
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
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

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
    private final ApplicationEventMulticaster applicationEventMulticaster;

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        log.info("Received chat request for conversation: {}, useTools: {}", request.getConversationId(), request.isUseTools());
        ChatResponse response = chatService.chat(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> chatStream(@Valid @RequestBody ChatRequest request) {
        log.info("Received streaming chat request for conversation: {}, useTools: {}", request.getConversationId(), request.isUseTools());

        // Per-request sink to relay WikiOpEvents from the application context as named SSE events.
        Sinks.Many<ServerSentEvent<Object>> wikiSink = Sinks.many().multicast().onBackpressureBuffer();

        UUID currentUserId = userService.getCurrentUser().map(User::getId).orElse(null);
        UUID requestConvId = request.getConversationId();

        ApplicationListener<WikiOpEvent> wikiListener = evt -> {
            if (currentUserId == null || !currentUserId.equals(evt.getUserId())) return;
            // Only filter by conversation if the request is bound to one and the event is too.
            if (requestConvId != null && evt.getConversationId() != null
                    && !requestConvId.equals(evt.getConversationId())) return;
            try {
                wikiSink.tryEmitNext(ServerSentEvent.<Object>builder()
                        .event("wiki_op")
                        .data(evt.getPayload())
                        .build());
            } catch (Exception ignored) {
                // sink closed
            }
        };
        applicationEventMulticaster.addApplicationListener(wikiListener);

        Flux<ServerSentEvent<Object>> chatFlux = chatService.chatStream(request)
                .map(resp -> ServerSentEvent.<Object>builder()
                        .event("message")
                        .data(resp)
                        .build());

        return Flux.merge(chatFlux, wikiSink.asFlux())
                .doFinally(sig -> {
                    applicationEventMulticaster.removeApplicationListener(wikiListener);
                    wikiSink.tryEmitComplete();
                });
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

        // Include individual tools from auto-discovered CF binding servers
        for (McpServerService service : mcpToolCallbackCacheService.getMcpServerServices()) {
            McpServerService.McpServerInfo healthCheck = service.getCachedHealthCheck();
            if (healthCheck == null) {
                // No cached data yet — trigger health check to populate tool list
                healthCheck = service.getHealthyMcpServer();
            }
            if (healthCheck.healthy()) {
                for (McpServerService.ToolInfo tool : healthCheck.tools()) {
                    Map<String, Object> toolData = new HashMap<>();
                    toolData.put("id", "binding-" + service.getName() + "-" + tool.name());
                    toolData.put("name", tool.name());
                    toolData.put("displayName", tool.name());
                    toolData.put("description", tool.description());
                    toolData.put("type", "MCP");
                    toolData.put("mcpServerName", service.getDisplayName());
                    result.add(toolData);
                }
            }
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
