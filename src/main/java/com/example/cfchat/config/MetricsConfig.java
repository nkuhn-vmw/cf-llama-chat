package com.example.cfchat.config;

import com.example.cfchat.service.ActiveUserTracker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@ConditionalOnClass(MeterRegistry.class)
public class MetricsConfig {
    private final MeterRegistry registry;

    @Bean
    Counter chatCounter() {
        return Counter.builder("cfllama.chat.requests")
                .description("Total chat requests")
                .register(registry);
    }

    @Bean
    Timer chatTimer() {
        return Timer.builder("cfllama.chat.duration")
                .description("Chat response generation duration")
                .register(registry);
    }

    @Bean
    Counter ragCounter() {
        return Counter.builder("cfllama.rag.searches")
                .description("Total RAG search requests")
                .register(registry);
    }

    @Bean
    Gauge activeUsersGauge(ActiveUserTracker tracker) {
        return Gauge.builder("cfllama.users.active", tracker, t -> t.getActiveSessions().size())
                .description("Current active users")
                .register(registry);
    }
}
