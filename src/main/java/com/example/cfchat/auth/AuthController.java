package com.example.cfchat.auth;

import com.example.cfchat.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
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
        return ResponseEntity.ok(provider);
    }
}
