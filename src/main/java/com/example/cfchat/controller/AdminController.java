package com.example.cfchat.controller;

import com.example.cfchat.auth.UserService;
import com.example.cfchat.model.User;
import com.example.cfchat.repository.ConversationRepository;
import com.example.cfchat.service.ConversationService;
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
public class AdminController {

    private final UserService userService;
    private final ConversationService conversationService;
    private final ConversationRepository conversationRepository;

    @GetMapping("/admin")
    public String adminPage(Model model) {
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

        return "admin";
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

        if (deleteConversations) {
            conversationRepository.deleteByUserId(userId);
        }

        userService.deleteUser(userId);
        log.info("User {} deleted by {}", userId, currentUser.get().getUsername());

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
}
