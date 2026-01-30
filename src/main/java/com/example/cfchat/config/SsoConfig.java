package com.example.cfchat.config;

import io.pivotal.cfenv.core.CfCredentials;
import io.pivotal.cfenv.core.CfEnv;
import io.pivotal.cfenv.core.CfService;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for Tanzu SSO (p-identity) service via VCAP_SERVICES.
 * Parses the p-identity service binding and exposes credentials as sso.* properties
 * for use by SecurityConfig.
 */
@Configuration
@Profile("cloud")
@Slf4j
public class SsoConfig {

    private final ConfigurableEnvironment environment;

    @Getter
    private boolean ssoConfigured = false;

    @Getter
    private String clientId;

    @Getter
    private String authUri;

    public SsoConfig(ConfigurableEnvironment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void init() {
        log.info("SsoConfig initializing - parsing VCAP_SERVICES for p-identity service");
        initializeSsoFromVcap();
    }

    private void initializeSsoFromVcap() {
        try {
            CfEnv cfEnv = new CfEnv();

            // Look for p-identity service
            List<CfService> ssoServices = cfEnv.findServicesByLabel("p-identity");

            if (ssoServices.isEmpty()) {
                log.info("No p-identity service found in VCAP_SERVICES - SSO will not be available");
                return;
            }

            CfService ssoService = ssoServices.get(0);
            String serviceName = ssoService.getName();
            CfCredentials credentials = ssoService.getCredentials();

            log.info("Found p-identity service: {}", serviceName);

            // Extract SSO credentials
            clientId = credentials.getString("client_id");
            String clientSecret = credentials.getString("client_secret");
            authUri = credentials.getString("auth_domain");

            // Build full URIs if auth_domain is just the domain
            String tokenUri = null;
            String userInfoUri = null;

            if (authUri != null && !authUri.isEmpty()) {
                // auth_domain might be just the domain or full URL
                if (!authUri.startsWith("http")) {
                    authUri = "https://" + authUri;
                }

                // Standard OAuth2 endpoints for UAA/p-identity
                String authBase = authUri.endsWith("/") ? authUri.substring(0, authUri.length() - 1) : authUri;
                authUri = authBase + "/oauth/authorize";
                tokenUri = authBase + "/oauth/token";
                userInfoUri = authBase + "/userinfo";
            }

            log.info("SSO configuration - clientId: {}, authUri: {}, tokenUri: {}",
                    clientId != null ? "present" : "missing",
                    authUri != null ? authUri : "missing",
                    tokenUri != null ? tokenUri : "missing");

            if (clientId != null && clientSecret != null && authUri != null) {
                // Set properties for SecurityConfig
                Map<String, Object> ssoProps = new HashMap<>();
                ssoProps.put("sso.client-id", clientId);
                ssoProps.put("sso.client-secret", clientSecret);
                ssoProps.put("sso.auth-uri", authUri);
                ssoProps.put("sso.token-uri", tokenUri);
                ssoProps.put("sso.user-info-uri", userInfoUri);

                // Add as property source with high priority
                environment.getPropertySources().addFirst(
                        new MapPropertySource("ssoProperties", ssoProps)
                );

                ssoConfigured = true;
                log.info("SSO configured successfully from p-identity service: {}", serviceName);
            } else {
                log.warn("p-identity service {} missing required credentials", serviceName);
                log.warn("  client_id: {}, client_secret: {}, auth_domain: {}",
                        clientId != null, clientSecret != null, authUri != null);
            }

        } catch (Exception e) {
            log.warn("Could not parse VCAP_SERVICES for p-identity service: {}", e.getMessage());
            log.debug("SSO parse error details", e);
        }
    }
}
