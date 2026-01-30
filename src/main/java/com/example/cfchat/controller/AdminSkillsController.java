package com.example.cfchat.controller;

import com.example.cfchat.auth.UserService;
import com.example.cfchat.model.Skill;
import com.example.cfchat.model.Tool;
import com.example.cfchat.model.User;
import com.example.cfchat.service.SkillService;
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
public class AdminSkillsController {

    private final UserService userService;
    private final SkillService skillService;
    private final ToolService toolService;

    @GetMapping("/admin/skills")
    public String skillsPage(Model model) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return "redirect:/";
        }

        List<Skill> skills = skillService.getAllSkills();
        List<Map<String, Object>> skillDataList = new ArrayList<>();

        for (Skill skill : skills) {
            Map<String, Object> skillData = new HashMap<>();
            skillData.put("skill", skill);

            List<Tool> skillTools = skillService.getSkillTools(skill);
            skillData.put("tools", skillTools);
            skillData.put("toolCount", skillTools.size());

            skillDataList.add(skillData);
        }

        model.addAttribute("skills", skillDataList);
        model.addAttribute("totalSkills", skills.size());
        model.addAttribute("enabledSkills", skills.stream().filter(Skill::isEnabled).count());

        // Add all available tools for the skill editor
        model.addAttribute("availableTools", toolService.getAllTools());

        return "admin/skills";
    }

    @GetMapping("/api/admin/skills")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getSkills() {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).build();
        }

        List<Skill> skills = skillService.getAllSkills();
        List<Map<String, Object>> result = new ArrayList<>();

        for (Skill skill : skills) {
            Map<String, Object> skillData = new HashMap<>();
            skillData.put("id", skill.getId());
            skillData.put("name", skill.getName());
            skillData.put("displayName", skill.getDisplayName());
            skillData.put("description", skill.getDescription());
            skillData.put("systemPromptAugmentation", skill.getSystemPromptAugmentation());
            skillData.put("toolIds", skill.getToolIds());
            skillData.put("enabled", skill.isEnabled());
            skillData.put("createdAt", skill.getCreatedAt());

            List<Tool> skillTools = skillService.getSkillTools(skill);
            skillData.put("toolCount", skillTools.size());

            List<Map<String, Object>> toolSummaries = new ArrayList<>();
            for (Tool tool : skillTools) {
                toolSummaries.add(Map.of(
                    "id", tool.getId(),
                    "name", tool.getName(),
                    "displayName", tool.getDisplayName() != null ? tool.getDisplayName() : tool.getName()
                ));
            }
            skillData.put("tools", toolSummaries);

            result.add(skillData);
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/admin/skills/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getSkill(@PathVariable UUID id) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).build();
        }

        return skillService.getSkillById(id)
            .map(skill -> {
                Map<String, Object> skillData = new HashMap<>();
                skillData.put("id", skill.getId());
                skillData.put("name", skill.getName());
                skillData.put("displayName", skill.getDisplayName());
                skillData.put("description", skill.getDescription());
                skillData.put("systemPromptAugmentation", skill.getSystemPromptAugmentation());
                skillData.put("toolIds", skill.getToolIds());
                skillData.put("enabled", skill.isEnabled());
                skillData.put("createdAt", skill.getCreatedAt());

                List<Tool> skillTools = skillService.getSkillTools(skill);
                skillData.put("toolCount", skillTools.size());

                return ResponseEntity.ok(skillData);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/admin/skills")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createSkill(@RequestBody Map<String, Object> body) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        try {
            String name = (String) body.get("name");
            String displayName = (String) body.get("displayName");
            String description = (String) body.get("description");
            String systemPromptAugmentation = (String) body.get("systemPromptAugmentation");
            String toolIds = (String) body.get("toolIds");

            if (name == null || name.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Name is required"));
            }

            // Handle toolIds as either a string or list
            Object toolIdsObj = body.get("toolIds");
            if (toolIdsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> toolIdsList = (List<String>) toolIdsObj;
                toolIds = skillService.serializeToolIds(
                    toolIdsList.stream().map(UUID::fromString).toList()
                );
            }

            Skill skill = Skill.builder()
                .name(name)
                .displayName(displayName)
                .description(description)
                .systemPromptAugmentation(systemPromptAugmentation)
                .toolIds(toolIds)
                .enabled(true)
                .build();

            Skill saved = skillService.createSkill(skill);
            log.info("Admin {} created skill: {}", currentUser.get().getUsername(), name);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "skillId", saved.getId(),
                "name", saved.getName()
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/api/admin/skills/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateSkill(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {

        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        try {
            String toolIds = null;
            Object toolIdsObj = body.get("toolIds");
            if (toolIdsObj instanceof String) {
                toolIds = (String) toolIdsObj;
            } else if (toolIdsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> toolIdsList = (List<String>) toolIdsObj;
                toolIds = skillService.serializeToolIds(
                    toolIdsList.stream().map(UUID::fromString).toList()
                );
            }

            Skill updates = Skill.builder()
                .name((String) body.get("name"))
                .displayName((String) body.get("displayName"))
                .description((String) body.get("description"))
                .systemPromptAugmentation((String) body.get("systemPromptAugmentation"))
                .toolIds(toolIds)
                .build();

            Skill updated = skillService.updateSkill(id, updates);
            log.info("Admin {} updated skill: {}", currentUser.get().getUsername(), updated.getName());

            return ResponseEntity.ok(Map.of("success", true, "skillId", updated.getId()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/api/admin/skills/{id}/enabled")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> setSkillEnabled(
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
            Skill skill = skillService.setEnabled(id, enabled);
            log.info("Admin {} set skill {} enabled: {}",
                currentUser.get().getUsername(), skill.getName(), enabled);

            return ResponseEntity.ok(Map.of("success", true, "enabled", skill.isEnabled()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/api/admin/skills/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteSkill(@PathVariable UUID id) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        try {
            skillService.deleteSkill(id);
            log.info("Admin {} deleted skill: {}", currentUser.get().getUsername(), id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
