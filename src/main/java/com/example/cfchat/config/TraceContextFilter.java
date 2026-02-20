package com.example.cfchat.config;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that adds the current trace and span IDs to HTTP response
 * headers so that clients (or a reverse proxy / service mesh) can correlate
 * requests across distributed services.
 *
 * <p>Headers written:
 * <ul>
 *   <li>{@code X-Trace-Id} - W3C trace ID (32 hex characters)</li>
 *   <li>{@code X-Span-Id} - current span ID (16 hex characters)</li>
 * </ul>
 *
 * <p>Only active when OTel export is enabled and a {@link Tracer} bean exists.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@ConditionalOnProperty(name = "management.otlp.metrics.export.enabled", havingValue = "true")
@ConditionalOnBean(Tracer.class)
public class TraceContextFilter extends OncePerRequestFilter {

    private final Tracer tracer;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            Span currentSpan = tracer.currentSpan();
            if (currentSpan != null && currentSpan.context() != null) {
                String traceId = currentSpan.context().traceId();
                String spanId = currentSpan.context().spanId();
                if (traceId != null) {
                    response.setHeader("X-Trace-Id", traceId);
                }
                if (spanId != null) {
                    response.setHeader("X-Span-Id", spanId);
                }
            }
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Skip static resources to reduce overhead
        String path = request.getRequestURI();
        return path.startsWith("/css/") ||
               path.startsWith("/js/") ||
               path.startsWith("/images/") ||
               path.startsWith("/webjars/") ||
               path.startsWith("/favicon");
    }
}
