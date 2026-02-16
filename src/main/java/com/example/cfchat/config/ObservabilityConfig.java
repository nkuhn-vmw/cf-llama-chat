package com.example.cfchat.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class ObservabilityConfig {

    @Bean
    public Counter chatRequestCounter(MeterRegistry registry) {
        return Counter.builder("cfllama.chat.requests")
                .description("Total number of chat requests")
                .register(registry);
    }

    @Bean
    public Timer chatDurationTimer(MeterRegistry registry) {
        return Timer.builder("cfllama.chat.duration")
                .description("Chat request duration")
                .register(registry);
    }

    @Bean
    public Counter ragSearchCounter(MeterRegistry registry) {
        return Counter.builder("cfllama.rag.searches")
                .description("Total number of RAG searches")
                .register(registry);
    }

    @Bean
    public Counter documentUploadCounter(MeterRegistry registry) {
        return Counter.builder("cfllama.documents.uploads")
                .description("Total number of document uploads")
                .register(registry);
    }

    @Bean
    public Counter webSearchCounter(MeterRegistry registry) {
        return Counter.builder("cfllama.web.searches")
                .description("Total number of web searches")
                .register(registry);
    }

    @Bean
    public Counter errorCounter(MeterRegistry registry) {
        return Counter.builder("cfllama.errors")
                .description("Total number of errors")
                .register(registry);
    }
}
