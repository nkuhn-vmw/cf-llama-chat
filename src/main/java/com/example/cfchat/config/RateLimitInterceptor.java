package com.example.cfchat.config;

import com.example.cfchat.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!rateLimitService.isEnabled()) return true;

        String path = request.getRequestURI();
        String action = resolveAction(path);
        if (action == null) return true;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return true;

        // Extract user info from authentication
        String username = auth.getName();
        String role = auth.getAuthorities().stream()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .findFirst()
                .orElse("USER");

        // Use username hash as UUID for rate limiting key
        UUID userKey = UUID.nameUUIDFromBytes(username.getBytes());

        if (!rateLimitService.tryConsume(userKey, role, action)) {
            long retryAfter = rateLimitService.getRetryAfterSeconds(userKey, role, action);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(Math.max(retryAfter, 1)));
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Rate limit exceeded\",\"retryAfter\":" + retryAfter + "}");
            return false;
        }

        return true;
    }

    private String resolveAction(String path) {
        if (path.startsWith("/api/chat")) return "chat";
        if (path.startsWith("/api/documents/upload")) return "upload";
        return null;
    }
}
