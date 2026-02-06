package com.example.cfchat.config;

import io.pivotal.cfenv.boot.genai.DefaultGenaiLocator;
import io.pivotal.cfenv.boot.genai.GenaiLocator;
import io.pivotal.cfenv.core.CfCredentials;
import io.pivotal.cfenv.core.CfEnv;
import io.pivotal.cfenv.core.CfService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;

import java.util.*;

/**
 * Configuration for Tanzu GenAI services via VCAP_SERVICES.
 * Supports both:
 * - GenAI Locator pattern (tanzu-all-models): single binding with endpoint containing config_url
 * - Direct binding pattern: individual service bindings with api_base, api_key, model_name
 */
@Configuration
@Profile("cloud")
@Slf4j
public class GenAiConfig {

    @Getter
    private final Map<String, ChatModel> chatModels = new LinkedHashMap<>();

    private final Map<String, ChatClient> chatClientCache = new java.util.concurrent.ConcurrentHashMap<>();

    @Getter
    private final Map<String, ModelMetadata> modelMetadata = new LinkedHashMap<>();

    @Getter
    private final List<GenaiLocator> genaiLocators = new ArrayList<>();

    private final RestClient.Builder restClientBuilder;

    public GenAiConfig() {
        this.restClientBuilder = RestClient.builder();
        log.info("GenAiConfig initializing - parsing VCAP_SERVICES for GenAI services");
        initializeModelsFromVcap();
    }

    private void initializeModelsFromVcap() {
        try {
            CfEnv cfEnv = new CfEnv();
            List<CfService> genaiServices = cfEnv.findServicesByLabel("genai");

            log.info("Found {} GenAI service(s) in VCAP_SERVICES", genaiServices.size());

            for (CfService service : genaiServices) {
                try {
                    if (isGenaiLocatorService(service)) {
                        // New GenAI Locator pattern - dynamically discover models
                        initializeFromGenaiLocator(service);
                    } else {
                        // Legacy direct binding pattern
                        initializeFromDirectBinding(service);
                    }
                } catch (Exception e) {
                    log.warn("Failed to configure GenAI service {}: {}", service.getName(), e.getMessage(), e);
                }
            }

            log.info("Total ChatModels registered: {}", chatModels.size());

        } catch (Exception e) {
            log.warn("Could not parse VCAP_SERVICES for GenAI services: {}", e.getMessage());
        }
    }

    /**
     * Checks if a service is a GenAI Locator service (has endpoint with config_url).
     */
    private boolean isGenaiLocatorService(CfService service) {
        Map<String, Object> credentials = service.getCredentials().getMap();
        if (credentials.containsKey("endpoint")) {
            Object endpoint = credentials.get("endpoint");
            if (endpoint instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> endpointMap = (Map<String, Object>) endpoint;
                return endpointMap.containsKey("config_url");
            }
        }
        return false;
    }

    /**
     * Initialize models from a GenAI Locator service.
     * Uses the GenaiLocator to discover available chat models dynamically.
     */
    @SuppressWarnings("unchecked")
    private void initializeFromGenaiLocator(CfService service) {
        String serviceName = service.getName();
        Map<String, Object> credentials = service.getCredentials().getMap();
        Map<String, Object> endpoint = (Map<String, Object>) credentials.get("endpoint");

        String configUrl = (String) endpoint.get("config_url");
        String apiKey = (String) endpoint.get("api_key");
        String apiBase = (String) endpoint.get("api_base");

        log.info("Initializing GenAI Locator service: {} (config_url: {})", serviceName, configUrl);

        if (configUrl == null || apiKey == null) {
            log.warn("GenAI Locator service {} missing required endpoint credentials", serviceName);
            return;
        }

        try {
            // Create GenaiLocator to discover models
            GenaiLocator locator = new DefaultGenaiLocator(restClientBuilder, configUrl, apiKey, apiBase);
            genaiLocators.add(locator);

            // Get available chat models from locator
            List<String> chatModelNames = locator.getModelNamesByCapability("CHAT");
            log.info("GenAI Locator {} provides {} chat model(s): {}", serviceName,
                    chatModelNames != null ? chatModelNames.size() : 0, chatModelNames);

            if (chatModelNames != null && !chatModelNames.isEmpty()) {
                for (String modelName : chatModelNames) {
                    // Skip embedding models
                    if (isEmbeddingModel(modelName)) {
                        log.info("Skipping embedding model for chat: {} (service: {})", modelName, serviceName);
                        continue;
                    }

                    try {
                        // Get the ChatModel from the locator for this specific model
                        ChatModel chatModel = locator.getChatModelByName(modelName);
                        if (chatModel != null) {
                            chatModels.put(modelName, chatModel);
                            modelMetadata.put(modelName, new ModelMetadata(
                                    modelName,
                                    serviceName,
                                    "GenaiLocator"
                            ));
                            log.info("Registered ChatModel from GenAI Locator: {} (service: {})", modelName, serviceName);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to get ChatModel for {} from GenAI Locator: {}", modelName, e.getMessage());
                    }
                }
            }

            // Log embedding models for informational purposes
            List<String> embeddingModelNames = locator.getModelNamesByCapability("EMBEDDING");
            if (embeddingModelNames != null && !embeddingModelNames.isEmpty()) {
                log.info("GenAI Locator {} provides {} embedding model(s): {}", serviceName,
                        embeddingModelNames.size(), embeddingModelNames);
            }

        } catch (Exception e) {
            log.warn("Failed to initialize GenAI Locator {}: {}", serviceName, e.getMessage(), e);
        }
    }

    /**
     * Initialize a model from a direct service binding (legacy pattern).
     */
    private void initializeFromDirectBinding(CfService service) {
        String serviceName = service.getName();
        CfCredentials credentials = service.getCredentials();

        // Get the API URL and key from credentials
        String apiUrl = credentials.getString("api_base");
        String apiKey = credentials.getString("api_key");
        String modelName = credentials.getString("model_name");

        // Fallbacks for different credential formats
        if (apiUrl == null) {
            apiUrl = credentials.getString("uri");
        }
        if (apiUrl == null) {
            apiUrl = credentials.getString("url");
        }
        if (modelName == null) {
            modelName = credentials.getString("model");
        }
        if (modelName == null) {
            // Use service instance name as model name if not specified
            modelName = serviceName;
        }

        log.info("Processing GenAI direct binding: {} (model: {}, url: {})",
                serviceName, modelName, apiUrl != null ? apiUrl : "not set");

        if (apiUrl != null && apiKey != null) {
            // Skip embedding models - they can't handle chat completions
            if (isEmbeddingModel(modelName) || isEmbeddingModel(serviceName)) {
                log.info("Skipping embedding model for chat: {} (service: {})", modelName, serviceName);
                return;
            }

            // Create OpenAI API client pointing to the GenAI service
            OpenAiApi openAiApi = OpenAiApi.builder()
                    .baseUrl(apiUrl)
                    .apiKey(apiKey)
                    .build();

            // Create ChatModel with default options
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .model(modelName)
                    .build();

            OpenAiChatModel chatModel = OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .defaultOptions(options)
                    .build();

            chatModels.put(modelName, chatModel);
            modelMetadata.put(modelName, new ModelMetadata(
                    modelName,
                    serviceName,
                    "OpenAiChatModel"
            ));

            log.info("Registered ChatModel from direct binding: {} (service: {})", modelName, serviceName);
        } else {
            log.warn("GenAI service {} missing required credentials (api_base: {}, api_key: {})",
                    serviceName, apiUrl != null, apiKey != null);
        }
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "spring.profiles.active", havingValue = "cloud")
    public ChatModel genaiChatModel() {
        if (!chatModels.isEmpty()) {
            String firstModelName = chatModels.keySet().iterator().next();
            ChatModel model = chatModels.get(firstModelName);
            log.info("Primary ChatModel set to: {}", firstModelName);
            return model;
        }

        log.warn("No GenAI ChatModel available - chat features will be disabled");
        return null;
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "spring.profiles.active", havingValue = "cloud")
    public ChatClient genaiChatClient() {
        ChatModel model = genaiChatModel();
        if (model != null) {
            log.info("Creating primary ChatClient with GenAI ChatModel");
            return ChatClient.builder(model).build();
        }
        log.warn("No ChatModel available - ChatClient will not be created");
        return null;
    }

    /**
     * Get a ChatModel by its model name.
     */
    public ChatModel getChatModelByName(String modelName) {
        ChatModel model = chatModels.get(modelName);
        if (model == null) {
            if (chatModels.isEmpty()) {
                log.error("No ChatModels available - check VCAP_SERVICES bindings");
                return null;
            }
            String defaultModel = chatModels.keySet().iterator().next();
            log.warn("ChatModel not found for name: '{}', available models: {}, using default: {}",
                    modelName, chatModels.keySet(), defaultModel);
            return chatModels.get(defaultModel);
        }
        return model;
    }

    /**
     * Get a ChatClient for a specific model (cached).
     */
    public ChatClient getChatClientForModel(String modelName) {
        ChatModel model = getChatModelByName(modelName);
        if (model != null) {
            return chatClientCache.computeIfAbsent(modelName, k -> ChatClient.builder(model).build());
        }
        return null;
    }

    /**
     * Returns true if any GenAI models are available via VCAP_SERVICES.
     */
    public boolean hasGenAiModels() {
        return !chatModels.isEmpty();
    }

    /**
     * Returns the list of available model names.
     */
    public List<String> getAvailableModelNames() {
        return new ArrayList<>(chatModels.keySet());
    }

    /**
     * Returns the number of configured models.
     */
    public int getModelCount() {
        return chatModels.size();
    }

    /**
     * Returns the list of GenaiLocators for use by other components (e.g., VectorStoreConfig).
     */
    public List<GenaiLocator> getGenaiLocators() {
        return Collections.unmodifiableList(genaiLocators);
    }

    /**
     * Checks if a model name indicates an embedding model (not suitable for chat).
     */
    private boolean isEmbeddingModel(String name) {
        if (name == null) {
            return false;
        }
        String lowerName = name.toLowerCase();
        return lowerName.contains("embed") ||
               lowerName.contains("embedding") ||
               lowerName.contains("nomic-embed") ||
               lowerName.contains("text-embedding") ||
               lowerName.contains("ada-002");
    }

    /**
     * Metadata about a configured model.
     */
    public record ModelMetadata(
            String modelName,
            String serviceName,
            String modelType
    ) {}
}
