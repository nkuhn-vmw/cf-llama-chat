package com.example.cfchat.auth;

import com.example.cfchat.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserService userService;
    private final SecurityConfig securityConfig;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getAuthStatus() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Map<String, Object> status = new HashMap<>();

        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            status.put("authenticated", true);
            status.put("username", userService.extractUsername(auth));

            Optional<User> user = userService.getCurrentUser();
            user.ifPresent(u -> {
                status.put("displayName", u.getDisplayName());
                status.put("email", u.getEmail());
                status.put("role", u.getRole().name());
                status.put("authProvider", u.getAuthProvider().name());
            });
        } else {
            status.put("authenticated", false);
        }

        return ResponseEntity.ok(status);
    }

    @GetMapping("/provider")
    public ResponseEntity<Map<String, Object>> getAuthProvider() {
        Map<String, Object> provider = new HashMap<>();
        provider.put("ssoEnabled", securityConfig.isSsoConfigured());
        provider.put("ssoUrl", securityConfig.isSsoConfigured() ? "/oauth2/authorization/sso" : null);
        provider.put("registrationEnabled", true);
        provider.put("invitationRequired", securityConfig.isInvitationRequired());
        return ResponseEntity.ok(provider);
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String email = body.get("email");
        String displayName = body.get("displayName");
        String invitationCode = body.get("invitationCode");

        Map<String, Object> response = new HashMap<>();

        // Validate required fields
        if (username == null || username.isBlank()) {
            response.put("success", false);
            response.put("error", "Username is required");
            return ResponseEntity.badRequest().body(response);
        }

        if (password == null || password.length() < 8
                || !password.matches(".*[A-Z].*")
                || !password.matches(".*[a-z].*")
                || !password.matches(".*\\d.*")) {
            response.put("success", false);
            response.put("error", "Password must be at least 8 characters with uppercase, lowercase, and a number");
            return ResponseEntity.badRequest().body(response);
        }

        // Validate username format
        if (!username.matches("^[a-zA-Z0-9_-]{3,30}$")) {
            response.put("success", false);
            response.put("error", "Username must be 3-30 characters and contain only letters, numbers, underscores, and hyphens");
            return ResponseEntity.badRequest().body(response);
        }

        // Check invitation code if required
        if (securityConfig.isInvitationRequired()) {
            if (invitationCode == null || !java.security.MessageDigest.isEqual(
                    invitationCode.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    securityConfig.getInvitationCode().getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                response.put("success", false);
                response.put("error", "Invalid invitation code");
                return ResponseEntity.badRequest().body(response);
            }
        }

        try {
            User user = userService.registerUser(username, password, email, displayName);
            log.info("User registered successfully: {}", username);

            response.put("success", true);
            response.put("username", user.getUsername());
            response.put("role", user.getRole().name());
            response.put("message", "Registration successful. Please log in.");

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Registration failed for {}: {}", username, e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("Registration error for {}: {}", username, e.getMessage());
            response.put("success", false);
            response.put("error", "Registration failed. Please try again.");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/check-username")
    public ResponseEntity<Map<String, Object>> checkUsername(@RequestParam String username) {
        Map<String, Object> response = new HashMap<>();
        boolean available = userService.isUsernameAvailable(username);
        response.put("available", available);
        response.put("username", username);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/check-email")
    public ResponseEntity<Map<String, Object>> checkEmail(@RequestParam String email) {
        Map<String, Object> response = new HashMap<>();
        boolean available = userService.isEmailAvailable(email);
        response.put("available", available);
        response.put("email", email);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(@RequestBody Map<String, String> body) {
        String currentPassword = body.get("currentPassword");
        String newPassword = body.get("newPassword");

        Map<String, Object> response = new HashMap<>();

        // Validate new password
        if (newPassword == null || newPassword.length() < 8
                || !newPassword.matches(".*[A-Z].*")
                || !newPassword.matches(".*[a-z].*")
                || !newPassword.matches(".*\\d.*")) {
            response.put("success", false);
            response.put("error", "New password must be at least 8 characters with uppercase, lowercase, and a number");
            return ResponseEntity.badRequest().body(response);
        }

        // Check if user can change password
        Optional<User> userOpt = userService.getCurrentUser();
        if (userOpt.isEmpty()) {
            response.put("success", false);
            response.put("error", "Not logged in");
            return ResponseEntity.status(401).body(response);
        }

        User user = userOpt.get();
        if (!userService.canChangePassword(user)) {
            response.put("success", false);
            response.put("error", "SSO users cannot change password here. Please use your SSO provider.");
            return ResponseEntity.badRequest().body(response);
        }

        // Attempt password change
        boolean success = userService.changePassword(currentPassword, newPassword);
        if (success) {
            log.info("Password changed for user: {}", user.getUsername());
            response.put("success", true);
            response.put("message", "Password changed successfully");
            return ResponseEntity.ok(response);
        } else {
            response.put("success", false);
            response.put("error", "Current password is incorrect");
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/can-change-password")
    public ResponseEntity<Map<String, Object>> canChangePassword() {
        Map<String, Object> response = new HashMap<>();
        Optional<User> userOpt = userService.getCurrentUser();

        if (userOpt.isEmpty()) {
            response.put("canChange", false);
            response.put("reason", "Not logged in");
        } else {
            User user = userOpt.get();
            boolean canChange = userService.canChangePassword(user);
            response.put("canChange", canChange);
            response.put("authProvider", user.getAuthProvider().name());
            if (!canChange) {
                response.put("reason", "SSO users cannot change password here");
            }
        }

        return ResponseEntity.ok(response);
    }
}
