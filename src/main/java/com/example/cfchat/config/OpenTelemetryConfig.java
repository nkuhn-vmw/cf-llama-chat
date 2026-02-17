package com.example.cfchat.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * OpenTelemetry-specific configuration activated only when OTEL_ENABLED=true
 * and the micrometer-tracing-bridge-otel library is on the classpath.
 *
 * <p>Spring Boot 3.x auto-configures the OTLP trace exporter and OTLP metrics
 * registry from {@code management.otlp.*} properties when the corresponding
 * dependencies are present. This class provides:
 * <ul>
 *   <li>Typed configuration properties under {@code otel.*} for the service
 *       name and custom resource attributes.</li>
 *   <li>Startup logging so operators can confirm OTel export is active.</li>
 * </ul>
 */
@Configuration
@ConditionalOnProperty(name = "management.otlp.metrics.export.enabled", havingValue = "true")
@ConditionalOnClass(name = "io.micrometer.tracing.otel.bridge.OtelTracer")
@ConfigurationProperties(prefix = "otel")
@Data
@Slf4j
public class OpenTelemetryConfig {

    /**
     * Logical service name reported to the OTel collector.
     * Defaults to "cf-llama-chat" if not set via OTEL_SERVICE_NAME.
     */
    private String serviceName = "cf-llama-chat";

    /**
     * Comma-separated list of extra OTel resource attributes
     * (e.g. "deployment.environment=production,service.namespace=chat").
     */
    private String resourceAttributes = "";

    @PostConstruct
    void logConfig() {
        log.info("OpenTelemetry export ENABLED  service.name={}, extra-attributes=[{}]",
                serviceName, resourceAttributes);
    }
}
