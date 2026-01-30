package com.example.cfchat.service;

import com.example.cfchat.model.*;
import com.example.cfchat.repository.McpServerRepository;
import com.example.cfchat.repository.SkillRepository;
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
public class UserAccessService {

    private final UserAccessRepository userAccessRepository;
    private final ToolRepository toolRepository;
    private final McpServerRepository mcpServerRepository;
    private final SkillRepository skillRepository;

    public record UserAccessSummary(
        List<UUID> toolIds,
        List<UUID> mcpServerIds,
        List<UUID> skillIds
    ) {}

    public record ResourceAccess(
        UUID resourceId,
        String name,
        String description,
        AccessType type,
        boolean hasAccess
    ) {}

    public List<UserAccess> getUserAccess(UUID userId) {
        return userAccessRepository.findByUserId(userId);
    }

    public UserAccessSummary getUserAccessSummary(UUID userId) {
        List<UUID> toolIds = userAccessRepository.findAllowedResourceIds(userId, AccessType.TOOL);
        List<UUID> mcpServerIds = userAccessRepository.findAllowedResourceIds(userId, AccessType.MCP_SERVER);
        List<UUID> skillIds = userAccessRepository.findAllowedResourceIds(userId, AccessType.SKILL);

        return new UserAccessSummary(toolIds, mcpServerIds, skillIds);
    }

    public boolean hasAccess(UUID userId, AccessType accessType, UUID resourceId) {
        return userAccessRepository.existsByUserIdAndAccessTypeAndResourceId(userId, accessType, resourceId);
    }

    @Transactional
    public UserAccess grantAccess(UUID userId, AccessType accessType, UUID resourceId) {
        // Validate resource exists
        validateResourceExists(accessType, resourceId);

        Optional<UserAccess> existing = userAccessRepository.findByUserIdAndAccessTypeAndResourceId(
            userId, accessType, resourceId);

        if (existing.isPresent()) {
            UserAccess access = existing.get();
            if (!access.isAllowed()) {
                access.setAllowed(true);
                return userAccessRepository.save(access);
            }
            return access;
        }

        UserAccess access = UserAccess.builder()
            .userId(userId)
            .accessType(accessType)
            .resourceId(resourceId)
            .allowed(true)
            .build();

        return userAccessRepository.save(access);
    }

    @Transactional
    public void revokeAccess(UUID userId, AccessType accessType, UUID resourceId) {
        Optional<UserAccess> existing = userAccessRepository.findByUserIdAndAccessTypeAndResourceId(
            userId, accessType, resourceId);

        if (existing.isPresent()) {
            UserAccess access = existing.get();
            access.setAllowed(false);
            userAccessRepository.save(access);
        }
    }

    @Transactional
    public void deleteAccess(UUID userId, AccessType accessType, UUID resourceId) {
        userAccessRepository.deleteByUserIdAndAccessTypeAndResourceId(userId, accessType, resourceId);
    }

    @Transactional
    public void updateUserAccess(UUID userId, AccessType accessType, List<UUID> resourceIds) {
        // Get current access
        List<UserAccess> currentAccess = userAccessRepository.findByUserIdAndAccessType(userId, accessType);
        Set<UUID> currentResourceIds = currentAccess.stream()
            .filter(UserAccess::isAllowed)
            .map(UserAccess::getResourceId)
            .collect(Collectors.toSet());

        Set<UUID> newResourceIds = new HashSet<>(resourceIds);

        // Grant access to new resources
        for (UUID resourceId : newResourceIds) {
            if (!currentResourceIds.contains(resourceId)) {
                grantAccess(userId, accessType, resourceId);
            }
        }

        // Revoke access from removed resources
        for (UUID resourceId : currentResourceIds) {
            if (!newResourceIds.contains(resourceId)) {
                revokeAccess(userId, accessType, resourceId);
            }
        }
    }

    @Transactional
    public void grantAllToolsAccess(UUID userId) {
        List<Tool> allTools = toolRepository.findAll();
        for (Tool tool : allTools) {
            grantAccess(userId, AccessType.TOOL, tool.getId());
        }
    }

    @Transactional
    public void grantAllSkillsAccess(UUID userId) {
        List<Skill> allSkills = skillRepository.findAll();
        for (Skill skill : allSkills) {
            grantAccess(userId, AccessType.SKILL, skill.getId());
        }
    }

    @Transactional
    public void grantAllMcpServersAccess(UUID userId) {
        List<McpServer> allServers = mcpServerRepository.findAll();
        for (McpServer server : allServers) {
            grantAccess(userId, AccessType.MCP_SERVER, server.getId());
        }
    }

    @Transactional
    public void revokeAllAccess(UUID userId) {
        List<UserAccess> allAccess = userAccessRepository.findByUserId(userId);
        for (UserAccess access : allAccess) {
            access.setAllowed(false);
        }
        userAccessRepository.saveAll(allAccess);
    }

    public List<ResourceAccess> getAllResourcesWithAccess(UUID userId) {
        List<ResourceAccess> resources = new ArrayList<>();

        // Get user's current access
        Set<UUID> allowedTools = new HashSet<>(userAccessRepository.findAllowedResourceIds(userId, AccessType.TOOL));
        Set<UUID> allowedServers = new HashSet<>(userAccessRepository.findAllowedResourceIds(userId, AccessType.MCP_SERVER));
        Set<UUID> allowedSkills = new HashSet<>(userAccessRepository.findAllowedResourceIds(userId, AccessType.SKILL));

        // Add all tools
        for (Tool tool : toolRepository.findAll()) {
            resources.add(new ResourceAccess(
                tool.getId(),
                tool.getDisplayName() != null ? tool.getDisplayName() : tool.getName(),
                tool.getDescription(),
                AccessType.TOOL,
                allowedTools.contains(tool.getId())
            ));
        }

        // Add all MCP servers
        for (McpServer server : mcpServerRepository.findAll()) {
            resources.add(new ResourceAccess(
                server.getId(),
                server.getName(),
                server.getDescription(),
                AccessType.MCP_SERVER,
                allowedServers.contains(server.getId())
            ));
        }

        // Add all skills
        for (Skill skill : skillRepository.findAll()) {
            resources.add(new ResourceAccess(
                skill.getId(),
                skill.getDisplayName() != null ? skill.getDisplayName() : skill.getName(),
                skill.getDescription(),
                AccessType.SKILL,
                allowedSkills.contains(skill.getId())
            ));
        }

        return resources;
    }

    private void validateResourceExists(AccessType accessType, UUID resourceId) {
        boolean exists = switch (accessType) {
            case TOOL -> toolRepository.existsById(resourceId);
            case MCP_SERVER -> mcpServerRepository.existsById(resourceId);
            case SKILL -> skillRepository.existsById(resourceId);
        };

        if (!exists) {
            throw new IllegalArgumentException(accessType.name() + " not found: " + resourceId);
        }
    }
}
