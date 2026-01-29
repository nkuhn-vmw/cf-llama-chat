package com.example.cfchat.config;

import io.pivotal.cfenv.boot.genai.GenaiLocator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.util.List;

/**
 * Configuration for Tanzu GenAI services via VCAP_SERVICES.
 * When running on Cloud Foundry with a bound GenAI service, this configuration
 * will automatically provide a ChatModel from the GenaiLocator.
 */
@Configuration
@Profile("cloud")
@Slf4j
public class GenAiConfig {

    private final List<GenaiLocator> genaiLocators;

    public GenAiConfig(List<GenaiLocator> genaiLocators) {
        this.genaiLocators = genaiLocators != null ? genaiLocators : List.of();
        log.info("GenAiConfig initialized with {} GenaiLocator(s)", this.genaiLocators.size());

        if (!this.genaiLocators.isEmpty()) {
            for (GenaiLocator locator : this.genaiLocators) {
                try {
                    List<String> chatModels = locator.getModelNamesByCapability("CHAT");
                    log.info("Available CHAT models from GenaiLocator: {}", chatModels);
                } catch (Exception e) {
                    log.debug("Could not retrieve model names from locator: {}", e.getMessage());
                }
            }
        }
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "spring.profiles.active", havingValue = "cloud")
    public ChatModel genaiChatModel() {
        for (GenaiLocator locator : genaiLocators) {
            try {
                ChatModel chatModel = locator.getFirstAvailableChatModel();
                if (chatModel != null) {
                    log.info("Using ChatModel from GenaiLocator: {}", chatModel.getClass().getSimpleName());
                    return chatModel;
                }
            } catch (Exception e) {
                log.warn("Failed to get ChatModel from GenaiLocator: {}", e.getMessage());
            }
        }
        throw new IllegalStateException("No ChatModel available from any GenaiLocator. " +
                "Ensure a GenAI service is bound to the application.");
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "spring.profiles.active", havingValue = "cloud")
    public ChatClient genaiChatClient(ChatModel genaiChatModel) {
        log.info("Creating primary ChatClient with GenAI ChatModel");
        return ChatClient.builder(genaiChatModel).build();
    }

    /**
     * Returns true if any GenAI models are available via VCAP_SERVICES.
     */
    public boolean hasGenAiModels() {
        return !genaiLocators.isEmpty();
    }

    /**
     * Returns the list of available model names from all locators.
     */
    public List<String> getAvailableModelNames() {
        return genaiLocators.stream()
                .flatMap(locator -> {
                    try {
                        return locator.getModelNamesByCapability("CHAT").stream();
                    } catch (Exception e) {
                        return java.util.stream.Stream.empty();
                    }
                })
                .toList();
    }
}
