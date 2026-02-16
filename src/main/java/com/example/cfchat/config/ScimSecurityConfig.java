package com.example.cfchat.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Security configuration for SCIM 2.0 endpoints.
 * Applies bearer token authentication to all /scim/** requests.
 */
@Configuration
@ConditionalOnProperty(name = "auth.scim.enabled", havingValue = "true")
@Slf4j
public class ScimSecurityConfig {

    @Value("${auth.scim.bearer-token:}")
    private String bearerToken;

    @Bean
    public FilterRegistrationBean<ScimBearerTokenFilter> scimBearerTokenFilter() {
        FilterRegistrationBean<ScimBearerTokenFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ScimBearerTokenFilter(bearerToken));
        registration.addUrlPatterns("/scim/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.setName("scimBearerTokenFilter");
        log.info("SCIM bearer token filter registered for /scim/* endpoints");
        return registration;
    }

    @Slf4j
    static class ScimBearerTokenFilter extends OncePerRequestFilter {

        private final String expectedToken;

        ScimBearerTokenFilter(String expectedToken) {
            this.expectedToken = expectedToken;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {

            if (expectedToken == null || expectedToken.isBlank()) {
                log.warn("SCIM bearer token is not configured - rejecting request to {}", request.getRequestURI());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/scim+json");
                response.getWriter().write("""
                        {"schemas":["urn:ietf:params:scim:api:messages:2.0:Error"],"detail":"SCIM bearer token not configured","status":"401"}""");
                return;
            }

            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.debug("Missing or invalid Authorization header for SCIM request: {}", request.getRequestURI());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/scim+json");
                response.getWriter().write("""
                        {"schemas":["urn:ietf:params:scim:api:messages:2.0:Error"],"detail":"Bearer token required","status":"401"}""");
                return;
            }

            String token = authHeader.substring(7);
            if (!expectedToken.equals(token)) {
                log.warn("Invalid SCIM bearer token for request: {}", request.getRequestURI());
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/scim+json");
                response.getWriter().write("""
                        {"schemas":["urn:ietf:params:scim:api:messages:2.0:Error"],"detail":"Invalid bearer token","status":"403"}""");
                return;
            }

            filterChain.doFilter(request, response);
        }
    }
}
