package com.example.cfchat.controller;

import com.example.cfchat.auth.UserService;
import com.example.cfchat.dto.OrganizationDto;
import com.example.cfchat.dto.OrganizationRequest;
import com.example.cfchat.dto.OrganizationThemeDto;
import com.example.cfchat.model.Organization;
import com.example.cfchat.model.User;
import com.example.cfchat.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
@RequiredArgsConstructor
@Slf4j
public class OrganizationController {

    private final OrganizationService organizationService;
    private final UserService userService;

    // ===== Admin UI Page =====

    @GetMapping("/admin/organizations")
    public String adminOrganizationsPage(Model model) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return "redirect:/";
        }

        List<OrganizationDto> organizations = organizationService.getAllOrganizationsIncludingInactive();
        model.addAttribute("organizations", organizations);
        model.addAttribute("organizationCount", organizations.size());

        // Get all users for the organization membership dropdown
        List<User> users = userService.getAllUsers();
        model.addAttribute("users", users);

        return "admin/organizations";
    }

    // ===== Public Theme API =====

    @GetMapping("/api/theme")
    @ResponseBody
    public ResponseEntity<OrganizationThemeDto> getCurrentTheme() {
        Optional<User> currentUser = userService.getCurrentUser();
        OrganizationThemeDto theme = organizationService.getThemeForUser(currentUser.orElse(null));
        return ResponseEntity.ok(theme);
    }

    @GetMapping("/api/theme/{slug}")
    @ResponseBody
    public ResponseEntity<OrganizationThemeDto> getThemeBySlug(@PathVariable String slug) {
        OrganizationThemeDto theme = organizationService.getThemeBySlug(slug);
        return ResponseEntity.ok(theme);
    }

    // ===== Admin Organization CRUD API =====

    @GetMapping("/api/admin/organizations")
    @ResponseBody
    public ResponseEntity<List<OrganizationDto>> getAllOrganizations() {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).build();
        }

        List<OrganizationDto> organizations = organizationService.getAllOrganizationsIncludingInactive();
        return ResponseEntity.ok(organizations);
    }

    @GetMapping("/api/admin/organizations/{id}")
    @ResponseBody
    public ResponseEntity<OrganizationDto> getOrganization(@PathVariable UUID id) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).build();
        }

        return organizationService.getOrganization(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/admin/organizations")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createOrganization(@RequestBody OrganizationRequest request) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        if (request.getName() == null || request.getName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Organization name is required"));
        }

        try {
            Organization org = organizationService.createOrganization(request);
            log.info("Organization {} created by {}", org.getName(), currentUser.get().getUsername());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "id", org.getId(),
                    "name", org.getName(),
                    "slug", org.getSlug()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/api/admin/organizations/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateOrganization(
            @PathVariable UUID id,
            @RequestBody OrganizationRequest request) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        try {
            Organization org = organizationService.updateOrganization(id, request);
            log.info("Organization {} updated by {}", org.getName(), currentUser.get().getUsername());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "id", org.getId(),
                    "name", org.getName(),
                    "slug", org.getSlug()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/api/admin/organizations/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteOrganization(@PathVariable UUID id) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        try {
            organizationService.deleteOrganization(id);
            log.info("Organization {} deleted by {}", id, currentUser.get().getUsername());

            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ===== Organization Membership API =====

    @GetMapping("/api/admin/organizations/{id}/members")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getOrganizationMembers(@PathVariable UUID id) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).build();
        }

        List<User> members = organizationService.getOrganizationMembers(id);
        List<Map<String, Object>> result = new ArrayList<>();

        for (User member : members) {
            Map<String, Object> memberData = new HashMap<>();
            memberData.put("id", member.getId());
            memberData.put("username", member.getUsername());
            memberData.put("email", member.getEmail());
            memberData.put("displayName", member.getDisplayName());
            memberData.put("organizationRole", member.getOrganizationRole().name());
            result.add(memberData);
        }

        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/admin/organizations/{orgId}/members/{userId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addMemberToOrganization(
            @PathVariable UUID orgId,
            @PathVariable UUID userId,
            @RequestBody(required = false) Map<String, String> body) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        String roleStr = body != null ? body.get("role") : null;
        User.OrganizationRole role = User.OrganizationRole.MEMBER;

        if (roleStr != null && !roleStr.isBlank()) {
            try {
                role = User.OrganizationRole.valueOf(roleStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid role: " + roleStr));
            }
        }

        try {
            organizationService.addUserToOrganization(userId, orgId, role);
            log.info("User {} added to organization {} with role {} by {}",
                    userId, orgId, role, currentUser.get().getUsername());

            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/api/admin/organizations/{orgId}/members/{userId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> removeMemberFromOrganization(
            @PathVariable UUID orgId,
            @PathVariable UUID userId) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        try {
            organizationService.removeUserFromOrganization(userId);
            log.info("User {} removed from organization {} by {}",
                    userId, orgId, currentUser.get().getUsername());

            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/api/admin/users/{userId}/organization")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateUserOrganization(
            @PathVariable UUID userId,
            @RequestBody Map<String, Object> body) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        String orgIdStr = body.get("organizationId") != null ? body.get("organizationId").toString() : null;
        String roleStr = body.get("organizationRole") != null ? body.get("organizationRole").toString() : null;

        try {
            if (orgIdStr == null || orgIdStr.isBlank() || "null".equals(orgIdStr)) {
                // Remove user from organization
                organizationService.removeUserFromOrganization(userId);
            } else {
                UUID orgId = UUID.fromString(orgIdStr);
                User.OrganizationRole role = User.OrganizationRole.MEMBER;

                if (roleStr != null && !roleStr.isBlank()) {
                    role = User.OrganizationRole.valueOf(roleStr.toUpperCase());
                }

                organizationService.addUserToOrganization(userId, orgId, role);
            }

            log.info("User {} organization updated by {}", userId, currentUser.get().getUsername());
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
