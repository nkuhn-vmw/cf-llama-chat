package com.example.cfchat.service;

import com.example.cfchat.model.AccessType;
import com.example.cfchat.model.Skill;
import com.example.cfchat.model.Tool;
import com.example.cfchat.repository.SkillRepository;
import com.example.cfchat.repository.UserAccessRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class SkillService {

    private final SkillRepository skillRepository;
    private final UserAccessRepository userAccessRepository;
    private final ToolService toolService;
    private final ObjectMapper objectMapper;

    public record SkillApplication(
        String augmentedSystemPrompt,
        List<Tool> tools
    ) {}

    public List<Skill> getAllSkills() {
        return skillRepository.findAll();
    }

    public List<Skill> getEnabledSkills() {
        return skillRepository.findByEnabled(true);
    }

    public Optional<Skill> getSkillById(UUID id) {
        return skillRepository.findById(id);
    }

    public Optional<Skill> getSkillByName(String name) {
        return skillRepository.findByName(name);
    }

    @Transactional
    public Skill createSkill(Skill skill) {
        if (skillRepository.existsByName(skill.getName())) {
            throw new IllegalArgumentException("Skill with name '" + skill.getName() + "' already exists");
        }
        return skillRepository.save(skill);
    }

    @Transactional
    public Skill updateSkill(UUID id, Skill updates) {
        Skill skill = skillRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + id));

        if (updates.getName() != null && !updates.getName().equals(skill.getName())) {
            if (skillRepository.existsByName(updates.getName())) {
                throw new IllegalArgumentException("Skill with name '" + updates.getName() + "' already exists");
            }
            skill.setName(updates.getName());
        }

        if (updates.getDisplayName() != null) {
            skill.setDisplayName(updates.getDisplayName());
        }
        if (updates.getDescription() != null) {
            skill.setDescription(updates.getDescription());
        }
        if (updates.getSystemPromptAugmentation() != null) {
            skill.setSystemPromptAugmentation(updates.getSystemPromptAugmentation());
        }
        if (updates.getToolIds() != null) {
            skill.setToolIds(updates.getToolIds());
        }

        return skillRepository.save(skill);
    }

    @Transactional
    public Skill setEnabled(UUID id, boolean enabled) {
        Skill skill = skillRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + id));
        skill.setEnabled(enabled);
        return skillRepository.save(skill);
    }

    @Transactional
    public void deleteSkill(UUID id) {
        if (!skillRepository.existsById(id)) {
            throw new IllegalArgumentException("Skill not found: " + id);
        }
        userAccessRepository.deleteByResourceIdAndAccessType(id, AccessType.SKILL);
        skillRepository.deleteById(id);
    }

    public List<Skill> getAccessibleSkills(UUID userId) {
        if (userId == null) {
            return List.of();
        }

        List<UUID> allowedSkillIds = userAccessRepository.findAllowedResourceIds(userId, AccessType.SKILL);
        if (allowedSkillIds.isEmpty()) {
            return List.of();
        }

        return skillRepository.findAllById(allowedSkillIds).stream()
            .filter(Skill::isEnabled)
            .toList();
    }

    public boolean hasAccess(UUID userId, UUID skillId) {
        if (userId == null || skillId == null) {
            return false;
        }
        return userAccessRepository.existsByUserIdAndAccessTypeAndResourceId(userId, AccessType.SKILL, skillId);
    }

    public SkillApplication applySkill(UUID skillId, String baseSystemPrompt) {
        Skill skill = skillRepository.findById(skillId)
            .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + skillId));

        if (!skill.isEnabled()) {
            throw new IllegalStateException("Skill is disabled: " + skill.getName());
        }

        // Build augmented system prompt
        String augmentedPrompt = baseSystemPrompt;
        if (skill.getSystemPromptAugmentation() != null && !skill.getSystemPromptAugmentation().isBlank()) {
            augmentedPrompt = baseSystemPrompt + "\n\n" + skill.getSystemPromptAugmentation();
        }

        // Get skill's tools
        List<Tool> tools = getSkillTools(skill);

        return new SkillApplication(augmentedPrompt, tools);
    }

    public List<Tool> getSkillTools(Skill skill) {
        if (skill.getToolIds() == null || skill.getToolIds().isBlank()) {
            return List.of();
        }

        List<UUID> toolIds = parseToolIds(skill.getToolIds());
        return toolService.getToolsByIds(toolIds);
    }

    public List<UUID> parseToolIds(String toolIdsJson) {
        if (toolIdsJson == null || toolIdsJson.isBlank()) {
            return List.of();
        }

        try {
            return objectMapper.readValue(toolIdsJson, new TypeReference<List<UUID>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse tool IDs JSON: {}", e.getMessage());
            return List.of();
        }
    }

    public String serializeToolIds(List<UUID> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return "[]";
        }

        try {
            return objectMapper.writeValueAsString(toolIds);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize tool IDs: {}", e.getMessage());
            return "[]";
        }
    }

    public long getSkillCount() {
        return skillRepository.count();
    }

    public long getEnabledSkillCount() {
        return skillRepository.findByEnabled(true).size();
    }
}
