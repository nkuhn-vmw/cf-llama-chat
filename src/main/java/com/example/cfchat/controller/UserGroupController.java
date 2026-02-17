package com.example.cfchat.controller;

import com.example.cfchat.model.User;
import com.example.cfchat.model.UserGroup;
import com.example.cfchat.service.UserGroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/groups")
@RequiredArgsConstructor
@Slf4j
public class UserGroupController {

    private final UserGroupService userGroupService;

    /**
     * GET /api/admin/groups - List all groups.
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listGroups() {
        List<UserGroup> groups = userGroupService.getAllGroups();
        List<Map<String, Object>> result = groups.stream()
                .map(this::groupToMap)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/admin/groups - Create a new group.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createGroup(@RequestBody Map<String, Object> body) {
        String name = body.get("name") != null ? body.get("name").toString() : null;
        String description = body.get("description") != null ? body.get("description").toString() : null;

        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Group name is required"));
        }

        try {
            UserGroup group = userGroupService.createGroup(name, description);
            log.info("Group created: {} (id: {})", name, group.getId());
            return ResponseEntity.ok(groupToMap(group));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * PUT /api/admin/groups/{id} - Update a group.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateGroup(@PathVariable Long id,
                                                            @RequestBody Map<String, Object> body) {
        String name = body.get("name") != null ? body.get("name").toString() : null;
        String description = body.get("description") != null ? body.get("description").toString() : null;

        try {
            UserGroup group = userGroupService.updateGroup(id, name, description);
            return ResponseEntity.ok(groupToMap(group));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * DELETE /api/admin/groups/{id} - Delete a group.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteGroup(@PathVariable Long id) {
        try {
            userGroupService.deleteGroup(id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/admin/groups/{id}/members/{userId} - Add a member to a group.
     */
    @PostMapping("/{id}/members/{userId}")
    public ResponseEntity<Map<String, Object>> addMember(@PathVariable Long id,
                                                          @PathVariable UUID userId) {
        try {
            UserGroup group = userGroupService.addMember(id, userId);
            return ResponseEntity.ok(groupToMap(group));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * DELETE /api/admin/groups/{id}/members/{userId} - Remove a member from a group.
     */
    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Map<String, Object>> removeMember(@PathVariable Long id,
                                                             @PathVariable UUID userId) {
        try {
            UserGroup group = userGroupService.removeMember(id, userId);
            return ResponseEntity.ok(groupToMap(group));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/admin/groups/{id}/members - List members of a group.
     */
    @GetMapping("/{id}/members")
    public ResponseEntity<List<Map<String, Object>>> listMembers(@PathVariable Long id) {
        try {
            Set<User> members = userGroupService.getGroupMembers(id);
            List<Map<String, Object>> result = members.stream()
                    .map(this::userToMap)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // --- Helper methods ---

    private Map<String, Object> groupToMap(UserGroup group) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", group.getId());
        map.put("name", group.getName());
        map.put("description", group.getDescription());
        map.put("memberCount", group.getMembers() != null ? group.getMembers().size() : 0);
        map.put("createdAt", group.getCreatedAt());
        return map;
    }

    private Map<String, Object> userToMap(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("email", user.getEmail());
        map.put("displayName", user.getDisplayName());
        map.put("role", user.getRole().name());
        map.put("authProvider", user.getAuthProvider().name());
        return map;
    }
}
