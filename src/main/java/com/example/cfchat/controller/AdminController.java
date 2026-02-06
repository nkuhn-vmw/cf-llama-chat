package com.example.cfchat.controller;

import com.example.cfchat.auth.UserService;
import com.example.cfchat.config.GenAiConfig;
import com.example.cfchat.model.AccessType;
import com.example.cfchat.model.ModelInfo;
import com.example.cfchat.model.User;
import com.example.cfchat.repository.ConversationRepository;
import com.example.cfchat.repository.EmbeddingMetricRepository;
import com.example.cfchat.repository.OrganizationRepository;
import com.example.cfchat.repository.UsageMetricRepository;
import com.example.cfchat.repository.UserAccessRepository;
import com.example.cfchat.repository.UserDocumentRepository;
import com.example.cfchat.service.ChatService;
import com.example.cfchat.service.ConversationService;
import com.example.cfchat.service.DatabaseStatsService;
import com.example.cfchat.service.UserAccessService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
@Slf4j
public class AdminController {

    private final UserService userService;
    private final ConversationService conversationService;
    private final ConversationRepository conversationRepository;
    private final ChatService chatService;
    private final GenAiConfig genAiConfig;
    private final OrganizationRepository organizationRepository;
    private final UserAccessService userAccessService;
    private final DatabaseStatsService databaseStatsService;
    private final UsageMetricRepository usageMetricRepository;
    private final EmbeddingMetricRepository embeddingMetricRepository;
    private final UserAccessRepository userAccessRepository;
    private final UserDocumentRepository userDocumentRepository;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    public AdminController(
            UserService userService,
            ConversationService conversationService,
            ConversationRepository conversationRepository,
            ChatService chatService,
            @Autowired(required = false) GenAiConfig genAiConfig,
            OrganizationRepository organizationRepository,
            UserAccessService userAccessService,
            DatabaseStatsService databaseStatsService,
            UsageMetricRepository usageMetricRepository,
            EmbeddingMetricRepository embeddingMetricRepository,
            UserAccessRepository userAccessRepository,
            @Autowired(required = false) UserDocumentRepository userDocumentRepository) {
        this.userService = userService;
        this.conversationService = conversationService;
        this.conversationRepository = conversationRepository;
        this.chatService = chatService;
        this.genAiConfig = genAiConfig;
        this.organizationRepository = organizationRepository;
        this.userAccessService = userAccessService;
        this.databaseStatsService = databaseStatsService;
        this.usageMetricRepository = usageMetricRepository;
        this.embeddingMetricRepository = embeddingMetricRepository;
        this.userAccessRepository = userAccessRepository;
        this.userDocumentRepository = userDocumentRepository;
    }

    @GetMapping("/admin")
    public String adminPage(Model model) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return "redirect:/";
        }

        List<User> users = userService.getAllUsers();
        model.addAttribute("totalUsers", users.size());
        model.addAttribute("totalAdmins", userService.getAdminCount());

        // Add model configuration info
        model.addAttribute("modelCount", chatService.getAvailableModels().size());

        // Add GenAI-specific info if available
        boolean isCloudProfile = activeProfile != null && activeProfile.contains("cloud");
        model.addAttribute("isCloudProfile", isCloudProfile);

        if (genAiConfig != null) {
            model.addAttribute("genAiModelCount", genAiConfig.getModelCount());
        } else {
            model.addAttribute("genAiModelCount", 0);
        }

        // Add organization count
        model.addAttribute("organizationCount", organizationRepository.count());

        return "admin";
    }

    @GetMapping("/admin/users")
    public String adminUsersPage(Model model) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return "redirect:/";
        }

        List<User> users = userService.getAllUsers();
        List<Map<String, Object>> userDataList = new ArrayList<>();

        for (User user : users) {
            Map<String, Object> userData = new HashMap<>();
            userData.put("user", user);
            userData.put("conversationCount", conversationService.getConversationCountForUser(user.getId()));
            userDataList.add(userData);
        }

        model.addAttribute("users", userDataList);
        model.addAttribute("currentUser", currentUser.get());
        model.addAttribute("totalUsers", users.size());
        model.addAttribute("totalAdmins", userService.getAdminCount());

        return "admin/users";
    }

    @GetMapping("/admin/models")
    public String adminModelsPage(Model model) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return "redirect:/";
        }

        var models = chatService.getAvailableModels();
        model.addAttribute("models", models);
        model.addAttribute("modelCount", models.size());

        // Count available models
        long availableCount = models.stream().filter(ModelInfo::isAvailable).count();
        model.addAttribute("availableCount", availableCount);

        // Add GenAI-specific info if available
        boolean isCloudProfile = activeProfile != null && activeProfile.contains("cloud");
        model.addAttribute("isCloudProfile", isCloudProfile);

        if (genAiConfig != null) {
            model.addAttribute("genAiModelCount", genAiConfig.getModelCount());
            model.addAttribute("genAiModelMetadata", genAiConfig.getModelMetadata());
        } else {
            model.addAttribute("genAiModelCount", 0);
            model.addAttribute("genAiModelMetadata", Map.of());
        }

        return "admin/models";
    }

    @GetMapping("/admin/storage")
    public String adminStoragePage(Model model) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return "redirect:/";
        }

        return "admin/storage";
    }

    @GetMapping("/admin/database")
    public String adminDatabasePage(Model model) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return "redirect:/";
        }

        model.addAttribute("dbOverview", databaseStatsService.getDatabaseOverview());
        model.addAttribute("tableStats", databaseStatsService.getTableStats());
        model.addAttribute("indexStats", databaseStatsService.getIndexStats());
        model.addAttribute("activeConnections", databaseStatsService.getActiveConnections());
        model.addAttribute("slowQueries", databaseStatsService.getSlowQueries());
        model.addAttribute("isPostgres", databaseStatsService.isPostgres());

        return "admin/database";
    }

    @GetMapping("/api/admin/database/stats")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getDatabaseStats() {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).build();
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("overview", databaseStatsService.getDatabaseOverview());
        stats.put("tables", databaseStatsService.getTableStats());
        stats.put("indexes", databaseStatsService.getIndexStats());
        stats.put("connections", databaseStatsService.getActiveConnections());
        stats.put("slowQueries", databaseStatsService.getSlowQueries());
        stats.put("isPostgres", databaseStatsService.isPostgres());

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/api/admin/models")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getModels() {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).build();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("models", chatService.getAvailableModels());
        result.put("totalCount", chatService.getAvailableModels().size());

        boolean isCloudProfile = activeProfile != null && activeProfile.contains("cloud");
        result.put("isCloudProfile", isCloudProfile);

        if (genAiConfig != null) {
            result.put("genAiModelCount", genAiConfig.getModelCount());
            result.put("genAiModelNames", genAiConfig.getAvailableModelNames());

            List<Map<String, String>> metadata = new ArrayList<>();
            genAiConfig.getModelMetadata().forEach((name, meta) -> {
                Map<String, String> m = new HashMap<>();
                m.put("modelName", meta.modelName());
                m.put("serviceName", meta.serviceName());
                m.put("modelType", meta.modelType());
                metadata.add(m);
            });
            result.put("genAiModelMetadata", metadata);
        }

        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/admin/users/{userId}/role")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateUserRole(
            @PathVariable UUID userId,
            @RequestBody Map<String, String> body) {

        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        String roleStr = body.get("role");
        if (roleStr == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Role is required"));
        }

        try {
            User.UserRole newRole = User.UserRole.valueOf(roleStr.toUpperCase());

            // Prevent demoting the last admin
            if (newRole == User.UserRole.USER) {
                Optional<User> targetUser = userService.getUserById(userId);
                if (targetUser.isPresent() && targetUser.get().getRole() == User.UserRole.ADMIN) {
                    if (userService.getAdminCount() <= 1) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("error", "Cannot demote the last admin"));
                    }
                }
            }

            User updatedUser = userService.updateUserRole(userId, newRole);
            log.info("User {} role updated to {} by {}", userId, newRole, currentUser.get().getUsername());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "userId", updatedUser.getId(),
                    "role", updatedUser.getRole().name()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid role: " + roleStr));
        }
    }

    @DeleteMapping("/api/admin/users/{userId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteUser(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "false") boolean deleteConversations) {

        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        // Prevent self-deletion
        if (currentUser.get().getId().equals(userId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot delete your own account"));
        }

        Optional<User> targetUser = userService.getUserById(userId);
        if (targetUser.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // Prevent deleting the last admin
        if (targetUser.get().getRole() == User.UserRole.ADMIN && userService.getAdminCount() <= 1) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot delete the last admin"));
        }

        // Clean up all user-related data
        conversationRepository.deleteByUserId(userId);
        usageMetricRepository.deleteByUserId(userId);
        embeddingMetricRepository.deleteByUserId(userId);
        userAccessRepository.deleteByUserId(userId);
        if (userDocumentRepository != null) {
            userDocumentRepository.deleteByUserId(userId);
        }

        userService.deleteUser(userId);
        log.info("User {} and all associated data deleted by {}", userId, currentUser.get().getUsername());

        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/api/admin/users")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getUsers() {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).build();
        }

        List<User> users = userService.getAllUsers();
        List<Map<String, Object>> result = new ArrayList<>();

        for (User user : users) {
            Map<String, Object> userData = new HashMap<>();
            userData.put("id", user.getId());
            userData.put("username", user.getUsername());
            userData.put("email", user.getEmail());
            userData.put("displayName", user.getDisplayName());
            userData.put("role", user.getRole().name());
            userData.put("authProvider", user.getAuthProvider().name());
            userData.put("createdAt", user.getCreatedAt());
            userData.put("lastLoginAt", user.getLastLoginAt());
            userData.put("conversationCount", conversationService.getConversationCountForUser(user.getId()));
            result.add(userData);
        }

        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/admin/users")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createUser(@RequestBody Map<String, String> body) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        String username = body.get("username");
        String password = body.get("password");
        String email = body.get("email");
        String displayName = body.get("displayName");
        String roleStr = body.get("role");

        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username is required"));
        }

        if (password == null || password.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 6 characters"));
        }

        try {
            User user = userService.registerUser(username, password, email, displayName);

            // Update role if specified and different from default
            if (roleStr != null && !roleStr.isBlank()) {
                User.UserRole role = User.UserRole.valueOf(roleStr.toUpperCase());
                if (role != user.getRole()) {
                    user = userService.updateUserRole(user.getId(), role);
                }
            }

            log.info("Admin {} created user: {} with role: {}",
                    currentUser.get().getUsername(), username, user.getRole());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "userId", user.getId(),
                    "username", user.getUsername(),
                    "role", user.getRole().name()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/admin/users/{userId}/access")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getUserAccess(@PathVariable UUID userId) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        Optional<User> targetUser = userService.getUserById(userId);
        if (targetUser.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        UserAccessService.UserAccessSummary summary = userAccessService.getUserAccessSummary(userId);
        List<UserAccessService.ResourceAccess> allResources = userAccessService.getAllResourcesWithAccess(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("toolIds", summary.toolIds());
        result.put("mcpServerIds", summary.mcpServerIds());
        result.put("skillIds", summary.skillIds());
        result.put("resources", allResources);

        return ResponseEntity.ok(result);
    }

    @PutMapping("/api/admin/users/{userId}/access")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateUserAccess(
            @PathVariable UUID userId,
            @RequestBody Map<String, Object> body) {

        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        Optional<User> targetUser = userService.getUserById(userId);
        if (targetUser.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        try {
            // Update tool access
            @SuppressWarnings("unchecked")
            List<String> toolIds = (List<String>) body.get("toolIds");
            if (toolIds != null) {
                List<UUID> toolUuids = toolIds.stream().map(UUID::fromString).toList();
                userAccessService.updateUserAccess(userId, AccessType.TOOL, toolUuids);
            }

            // Update MCP server access
            @SuppressWarnings("unchecked")
            List<String> mcpServerIds = (List<String>) body.get("mcpServerIds");
            if (mcpServerIds != null) {
                List<UUID> serverUuids = mcpServerIds.stream().map(UUID::fromString).toList();
                userAccessService.updateUserAccess(userId, AccessType.MCP_SERVER, serverUuids);
            }

            // Update skill access
            @SuppressWarnings("unchecked")
            List<String> skillIds = (List<String>) body.get("skillIds");
            if (skillIds != null) {
                List<UUID> skillUuids = skillIds.stream().map(UUID::fromString).toList();
                userAccessService.updateUserAccess(userId, AccessType.SKILL, skillUuids);
            }

            log.info("Admin {} updated access for user {}", currentUser.get().getUsername(), userId);

            return ResponseEntity.ok(Map.of("success", true));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/admin/users/{userId}/access/grant-all")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> grantAllAccess(
            @PathVariable UUID userId,
            @RequestBody Map<String, String> body) {

        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        Optional<User> targetUser = userService.getUserById(userId);
        if (targetUser.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String accessType = body.get("accessType");
        if (accessType == null) {
            // Grant all access of all types
            userAccessService.grantAllToolsAccess(userId);
            userAccessService.grantAllMcpServersAccess(userId);
            userAccessService.grantAllSkillsAccess(userId);
        } else {
            switch (accessType.toUpperCase()) {
                case "TOOL" -> userAccessService.grantAllToolsAccess(userId);
                case "MCP_SERVER" -> userAccessService.grantAllMcpServersAccess(userId);
                case "SKILL" -> userAccessService.grantAllSkillsAccess(userId);
                default -> {
                    return ResponseEntity.badRequest().body(Map.of("error", "Invalid access type"));
                }
            }
        }

        log.info("Admin {} granted all {} access to user {}",
            currentUser.get().getUsername(), accessType != null ? accessType : "ALL", userId);

        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/api/admin/users/{userId}/access/revoke-all")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> revokeAllAccess(@PathVariable UUID userId) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        Optional<User> targetUser = userService.getUserById(userId);
        if (targetUser.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        userAccessService.revokeAllAccess(userId);
        log.info("Admin {} revoked all access for user {}", currentUser.get().getUsername(), userId);

        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/api/admin/users/{userId}/reset-password")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> resetUserPassword(
            @PathVariable UUID userId,
            @RequestBody Map<String, String> body) {

        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty() || currentUser.get().getRole() != User.UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        Optional<User> targetUser = userService.getUserById(userId);
        if (targetUser.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // Cannot reset password for SSO users
        if (targetUser.get().getAuthProvider() == User.AuthProvider.SSO) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Cannot reset password for SSO users. They authenticate via their SSO provider."
            ));
        }

        String newPassword = body.get("newPassword");
        if (newPassword == null || newPassword.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 6 characters"));
        }

        boolean success = userService.resetUserPassword(userId, newPassword);
        if (success) {
            log.info("Admin {} reset password for user {}", currentUser.get().getUsername(), userId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Password reset successfully"
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to reset password"));
        }
    }
}
