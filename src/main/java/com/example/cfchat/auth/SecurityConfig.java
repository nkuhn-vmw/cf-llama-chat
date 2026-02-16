package com.example.cfchat.auth;

import com.example.cfchat.model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.cfchat.config.SsoConfig;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Configuration
@EnableWebSecurity
@Slf4j
public class SecurityConfig {

    @Value("${app.auth.secret:}")
    private String invitationCode;

    @Value("${app.auth.require-invitation:false}")
    private boolean requireInvitation;

    private final Environment environment;
    private final UserService userService;
    private final Optional<SsoConfig> ssoConfig;

    public SecurityConfig(Environment environment, UserService userService, Optional<SsoConfig> ssoConfig) {
        this.environment = environment;
        this.userService = userService;
        this.ssoConfig = ssoConfig;
        log.info("SecurityConfig initialized, ssoConfig present: {}", ssoConfig.isPresent());
    }

    public String getInvitationCode() {
        return invitationCode;
    }

    public boolean isInvitationRequired() {
        return requireInvitation && invitationCode != null && !invitationCode.isBlank();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .headers(headers -> {
                headers.contentTypeOptions(contentType -> {});
                headers.frameOptions(frame -> frame.deny());
                headers.httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .preload(true)
                    .maxAgeInSeconds(31536000));
                headers.referrerPolicy(referrer -> referrer
                    .policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                headers.permissionsPolicy(permissions -> permissions
                    .policy("geolocation=(), microphone=(), camera=(), payment=()"));
                headers.contentSecurityPolicy(csp -> csp
                    .policyDirectives("default-src 'self'; script-src 'self' https://cdnjs.cloudflare.com; style-src 'self' 'unsafe-inline' https://cdnjs.cloudflare.com; img-src 'self' data: https:; font-src 'self' https://cdnjs.cloudflare.com; connect-src 'self'; frame-ancestors 'none'"));
            })
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers("/auth/**", "/logout")
            )
            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login.html", "/register.html", "/auth/provider", "/auth/login", "/auth/register", "/auth/check-username", "/auth/check-email", "/actuator/health", "/css/**", "/js/**", "/manifest.json", "/sw.js", "/error").permitAll()
                .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login.html")
                .loginProcessingUrl("/auth/login")
                .successHandler(authenticationSuccessHandler())
                .failureUrl("/login.html?error=true")
                .permitAll()
            )
            .sessionManagement(session -> session
                .sessionFixation(fix -> fix.migrateSession())
                .maximumSessions(3)
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login.html?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            );

        // Add OAuth2 login if SSO is configured
        if (isSsoConfigured()) {
            log.info("SSO is configured - enabling OAuth2 login");
            http.oauth2Login(oauth2 -> oauth2
                .loginPage("/login.html")
                .successHandler(oauth2AuthenticationSuccessHandler())
                .failureHandler((request, response, exception) -> {
                    log.error("OAuth2 authentication failed: {}", exception.getMessage(), exception);
                    response.sendRedirect("/login.html?error=true&oauth_error=authentication_failed");
                })
            );
        } else {
            log.info("SSO is not configured - OAuth2 login disabled");
        }

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder authBuilder = http.getSharedObject(AuthenticationManagerBuilder.class);
        authBuilder.authenticationProvider(formLoginAuthenticationProvider());
        return authBuilder.build();
    }

    @Bean
    public org.springframework.security.authentication.AuthenticationProvider formLoginAuthenticationProvider() {
        return new org.springframework.security.authentication.AuthenticationProvider() {
            @Override
            public Authentication authenticate(Authentication authentication) throws org.springframework.security.core.AuthenticationException {
                String username = authentication.getName();
                String password = authentication.getCredentials().toString();

                log.debug("Form login authentication attempt for user: {}", username);

                try {
                    java.util.Optional<User> userOpt = userService.authenticateUser(username, password);

                    if (userOpt.isEmpty()) {
                        log.warn("Authentication failed for user: {}", username);
                        throw new BadCredentialsException("Invalid username or password");
                    }

                    User user = userOpt.get();
                    log.info("User authenticated: {} with role: {}", username, user.getRole());

                    List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                        new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
                    );

                    return new UsernamePasswordAuthenticationToken(username, null, authorities);
                } catch (BadCredentialsException e) {
                    throw e;
                } catch (Exception e) {
                    log.error("Error authenticating user {}: {}", username, e.getMessage());
                    throw new BadCredentialsException("Authentication failed: " + e.getMessage());
                }
            }

            @Override
            public boolean supports(Class<?> authentication) {
                // Only handle UsernamePasswordAuthenticationToken (form login)
                return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
            }
        };
    }

    private AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            log.info("User {} logged in successfully", authentication.getName());
            response.sendRedirect("/");
        };
    }

    private AuthenticationSuccessHandler oauth2AuthenticationSuccessHandler() {
        return (request, response, authentication) -> {
            OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();

            String username = oauth2User.getAttribute("preferred_username");
            if (username == null) {
                username = oauth2User.getAttribute("email");
            }
            if (username == null) {
                username = oauth2User.getAttribute("sub");
            }

            String email = oauth2User.getAttribute("email");
            String displayName = oauth2User.getAttribute("name");

            // Get or create user from OAuth2 info
            User user = userService.getOrCreateUser(username, email, displayName, User.AuthProvider.SSO);

            // Update the authentication with proper roles
            List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
            );

            Authentication newAuth = new UsernamePasswordAuthenticationToken(
                authentication.getPrincipal(),
                authentication.getCredentials(),
                authorities
            );

            SecurityContextHolder.getContext().setAuthentication(newAuth);
            request.getSession().setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                SecurityContextHolder.getContext()
            );

            log.info("SSO User {} logged in successfully with role {}", username, user.getRole());
            response.sendRedirect("/");
        };
    }

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        if (isSsoConfigured()) {
            String clientId = environment.getProperty("sso.client-id", "");
            String clientSecret = environment.getProperty("sso.client-secret", "");
            String authUri = environment.getProperty("sso.auth-uri", "");
            String tokenUri = environment.getProperty("sso.token-uri", "");
            String userInfoUri = environment.getProperty("sso.user-info-uri", "");
            String redirectUri = environment.getProperty("sso.redirect-uri", "{baseUrl}/login/oauth2/code/{registrationId}");

            // Derive JWK Set URI from auth base URL for JWT verification
            String authBase = authUri.replace("/oauth/authorize", "");
            String jwkSetUri = authBase + "/token_keys";

            ClientRegistration clientRegistration = ClientRegistration.withRegistrationId("sso")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(redirectUri)
                .scope("openid")  // Only use openid scope - profile may not be available
                .authorizationUri(authUri)
                .tokenUri(tokenUri)
                .userInfoUri(userInfoUri)
                .jwkSetUri(jwkSetUri)
                .userNameAttributeName("sub")
                .clientName("SSO")
                .build();

            log.info("SSO Client Registration created - jwkSetUri: {}", jwkSetUri);

            return new InMemoryClientRegistrationRepository(clientRegistration);
        }

        // Return a dummy registration when SSO is not configured
        // This prevents startup errors while keeping the bean available
        ClientRegistration dummyRegistration = ClientRegistration.withRegistrationId("none")
            .clientId("none")
            .clientSecret("none")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .authorizationUri("https://none/authorize")
            .tokenUri("https://none/token")
            .userInfoUri("https://none/userinfo")
            .userNameAttributeName("sub")
            .clientName("None")
            .build();

        return new InMemoryClientRegistrationRepository(dummyRegistration);
    }

    public boolean isSsoConfigured() {
        boolean configured = ssoConfig.map(SsoConfig::isSsoConfigured).orElse(false);
        log.debug("SSO configured check - ssoConfig present: {}, result: {}",
                ssoConfig.isPresent(), configured);
        return configured;
    }

    /**
     * Filter that eagerly loads the CSRF token so the XSRF-TOKEN cookie is set on every response.
     * Required for SPA clients that need to read the token from the cookie.
     */
    private static class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (csrfToken != null) {
                csrfToken.getToken(); // Force token generation and cookie setting
            }
            filterChain.doFilter(request, response);
        }
    }
}
