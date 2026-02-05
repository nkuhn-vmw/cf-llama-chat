package com.example.cfchat.service;

import com.example.cfchat.model.AccessType;
import com.example.cfchat.model.Tool;
import com.example.cfchat.model.ToolType;
import com.example.cfchat.repository.ToolRepository;
import com.example.cfchat.repository.UserAccessRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ToolService {

    private final ToolRepository toolRepository;
    private final UserAccessRepository userAccessRepository;

    public List<Tool> getAllTools() {
        return toolRepository.findAll();
    }

    public List<Tool> getEnabledTools() {
        return toolRepository.findByEnabled(true);
    }

    public Optional<Tool> getToolById(UUID id) {
        return toolRepository.findById(id);
    }

    public Optional<Tool> getToolByName(String name) {
        return toolRepository.findByName(name);
    }

    public List<Tool> getToolsByMcpServer(UUID mcpServerId) {
        return toolRepository.findByMcpServerId(mcpServerId);
    }

    public List<Tool> getToolsByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return toolRepository.findAllById(ids);
    }

    @Transactional
    public Tool createTool(Tool tool) {
        if (toolRepository.existsByName(tool.getName())) {
            throw new IllegalArgumentException("Tool with name '" + tool.getName() + "' already exists");
        }
        return toolRepository.save(tool);
    }

    @Transactional
    public Tool updateTool(UUID id, Tool updates) {
        Tool tool = toolRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Tool not found: " + id));

        if (updates.getDisplayName() != null) {
            tool.setDisplayName(updates.getDisplayName());
        }
        if (updates.getDescription() != null) {
            tool.setDescription(updates.getDescription());
        }
        if (updates.getInputSchema() != null) {
            tool.setInputSchema(updates.getInputSchema());
        }

        return toolRepository.save(tool);
    }

    @Transactional
    public Tool setEnabled(UUID id, boolean enabled) {
        Tool tool = toolRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Tool not found: " + id));
        tool.setEnabled(enabled);
        return toolRepository.save(tool);
    }

    @Transactional
    public void deleteTool(UUID id) {
        if (!toolRepository.existsById(id)) {
            throw new IllegalArgumentException("Tool not found: " + id);
        }
        userAccessRepository.deleteByResourceIdAndAccessType(id, AccessType.TOOL);
        toolRepository.deleteById(id);
    }

    public List<Tool> getAccessibleTools(UUID userId) {
        if (userId == null) {
            return List.of();
        }

        List<UUID> allowedToolIds = userAccessRepository.findAllowedResourceIds(userId, AccessType.TOOL);
        if (allowedToolIds.isEmpty()) {
            // No explicit access configured for this user - fall back to all enabled tools
            return toolRepository.findByEnabled(true);
        }

        return toolRepository.findEnabledByIds(allowedToolIds);
    }

    public boolean hasAccess(UUID userId, UUID toolId) {
        if (userId == null || toolId == null) {
            return false;
        }
        return userAccessRepository.existsByUserIdAndAccessTypeAndResourceId(userId, AccessType.TOOL, toolId);
    }

    public List<Tool> getToolsForSkill(String toolIdsJson) {
        if (toolIdsJson == null || toolIdsJson.isBlank()) {
            return List.of();
        }

        try {
            // Parse JSON array of UUIDs
            String cleaned = toolIdsJson.trim();
            if (cleaned.startsWith("[")) {
                cleaned = cleaned.substring(1);
            }
            if (cleaned.endsWith("]")) {
                cleaned = cleaned.substring(0, cleaned.length() - 1);
            }

            List<UUID> ids = Arrays.stream(cleaned.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.replace("\"", ""))
                .map(UUID::fromString)
                .collect(Collectors.toList());

            return toolRepository.findAllById(ids);
        } catch (Exception e) {
            log.warn("Failed to parse tool IDs: {}", e.getMessage());
            return List.of();
        }
    }

    public long getToolCount() {
        return toolRepository.count();
    }

    public long getEnabledToolCount() {
        return toolRepository.findByEnabled(true).size();
    }
}
