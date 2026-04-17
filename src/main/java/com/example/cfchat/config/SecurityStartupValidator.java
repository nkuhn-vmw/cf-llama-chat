package com.example.cfchat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Set;

/**
 * Refuses to start the app on the {@code cloud} profile if the shipped
 * "default" secrets are still in use. Protects operators from deploying with
 * the documented weak values ({@code Tanzu123}, {@code changeme}) still
 * active — these are fine for local development but must never reach a CF
 * foundation.
 */
@Component
@Slf4j
public class SecurityStartupValidator {

    private static final Set<String> WEAK_PASSWORDS = Set.of(
            "Tanzu123", "tanzu123", "admin", "password", "changeme");
    private static final Set<String> WEAK_AUTH_SECRETS = Set.of(
            "changeme", "changeme-cdc-wiki");

    private final Environment environment;

    @Value("${app.admin.default-password:}")
    private String adminPassword;

    @Value("${app.auth.secret:}")
    private String authSecret;

    public SecurityStartupValidator(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void validate() {
        boolean cloudProfile = Arrays.asList(environment.getActiveProfiles()).contains("cloud");
        if (!cloudProfile) {
            return;
        }

        if (adminPassword != null && !adminPassword.isBlank()
                && WEAK_PASSWORDS.contains(adminPassword)) {
            fail("APP_ADMIN_DEFAULT_PASSWORD is set to a known-weak value. "
                    + "Rotate via `cf set-env <app> APP_ADMIN_DEFAULT_PASSWORD <strong-value>` "
                    + "or unset it to let the app generate a random password on first boot.");
        }

        if (authSecret != null && !authSecret.isBlank()
                && WEAK_AUTH_SECRETS.contains(authSecret)) {
            fail("APP_AUTH_SECRET is set to a known-weak default. "
                    + "Rotate via `cf set-env <app> APP_AUTH_SECRET <strong-value>`.");
        }
    }

    private void fail(String reason) {
        log.error("=============================================================");
        log.error("  Startup refused: {}", reason);
        log.error("=============================================================");
        throw new IllegalStateException(reason);
    }
}
