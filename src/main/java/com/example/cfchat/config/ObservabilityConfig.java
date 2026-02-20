package com.example.cfchat.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Observability configuration that integrates Micrometer Observation API with
 * OpenTelemetry distributed tracing. When OTEL_ENABLED=true the tracing bridge,
 * OTLP exporter, and OTLP metrics registry are activated automatically by
 * Spring Boot auto-configuration via the optional dependencies declared in pom.xml.
 *
 * <p>This class provides:
 * <ul>
 *   <li>{@link ObservedAspect} so that {@code @Observed} annotations on service
 *       methods produce Micrometer observations (and therefore OTel spans when
 *       the bridge is on the classpath).</li>
 *   <li>A startup log indicating whether OTel export is active.</li>
 * </ul>
 *
 * <p>When OTEL_ENABLED is false (the default) no OTel exporter is instantiated
 * and the tracing bridge is a no-op, giving zero overhead.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class ObservabilityConfig {

    /**
     * Enables the {@code @Observed} annotation across the application.
     * Every method annotated with {@code @Observed} will create a Micrometer
     * {@link io.micrometer.observation.Observation} that is automatically
     * bridged to an OpenTelemetry span when the micrometer-tracing-bridge-otel
     * dependency is present.
     */
    @Bean
    @ConditionalOnBean(ObservationRegistry.class)
    public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        log.info("ObservedAspect registered - @Observed annotations will produce trace spans");
        return new ObservedAspect(observationRegistry);
    }
}
