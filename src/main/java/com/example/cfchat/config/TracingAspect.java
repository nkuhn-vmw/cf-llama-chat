package com.example.cfchat.config;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * AOP aspect that creates Micrometer Observation spans around service methods
 * that are not individually annotated with {@code @Observed}. When the OTel
 * tracing bridge is on the classpath each observation automatically becomes a
 * distributed trace span.
 *
 * <p>Activated only when {@code management.otlp.metrics.export.enabled=true}
 * so there is zero overhead when tracing is disabled.
 *
 * <p>Methods that already carry {@code @Observed} are handled by the
 * {@link io.micrometer.observation.aop.ObservedAspect} bean (registered in
 * {@link ObservabilityConfig}) and are <b>excluded</b> from this aspect to
 * avoid double-wrapping.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "management.otlp.metrics.export.enabled", havingValue = "true")
@ConditionalOnBean(ObservationRegistry.class)
public class TracingAspect {

    private final ObservationRegistry observationRegistry;

    // ---- Pointcuts for internal / un-annotated service methods ----

    @Pointcut("execution(* com.example.cfchat.service.MetricsService.recordUsage(..))")
    void recordUsageMethod() {}

    @Pointcut("execution(* com.example.cfchat.service.MetricsService.recordEmbeddingUsage(..))")
    void recordEmbeddingUsageMethod() {}

    @Pointcut("execution(* com.example.cfchat.service.ActiveUserTracker.record(..))")
    void activeUserRecordMethod() {}

    // ---- Advice ----

    @Around("recordUsageMethod()")
    public Object traceRecordUsage(ProceedingJoinPoint joinPoint) throws Throwable {
        return observeMethod(joinPoint, "cfllama.metrics.record-usage",
                "Record chat usage metrics");
    }

    @Around("recordEmbeddingUsageMethod()")
    public Object traceRecordEmbeddingUsage(ProceedingJoinPoint joinPoint) throws Throwable {
        return observeMethod(joinPoint, "cfllama.metrics.record-embedding-usage",
                "Record embedding usage metrics");
    }

    @Around("activeUserRecordMethod()")
    public Object traceActiveUserRecord(ProceedingJoinPoint joinPoint) throws Throwable {
        return observeMethod(joinPoint, "cfllama.users.record-activity",
                "Record user activity");
    }

    // ---- Shared helper ----

    /**
     * Wraps a method invocation in a Micrometer {@link Observation} that is
     * automatically bridged to an OTel span when the tracing bridge is present.
     *
     * <p>On success the observation closes normally. On error the exception is
     * recorded on the observation (and therefore on the OTel span) before being
     * re-thrown so application error handling is unaffected.
     */
    private Object observeMethod(ProceedingJoinPoint joinPoint, String spanName,
                                  String description) throws Throwable {
        Observation observation = Observation.createNotStarted(spanName, observationRegistry)
                .lowCardinalityKeyValue("component", extractComponent(joinPoint))
                .contextualName(description);

        return observation.observeChecked(() -> {
            try {
                return joinPoint.proceed();
            } catch (Throwable t) {
                if (t instanceof RuntimeException re) {
                    throw re;
                }
                if (t instanceof Error err) {
                    throw err;
                }
                throw new RuntimeException(t);
            }
        });
    }

    private String extractComponent(ProceedingJoinPoint joinPoint) {
        return joinPoint.getTarget().getClass().getSimpleName();
    }
}
