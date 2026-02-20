package com.example.cfchat.config;

import com.example.cfchat.service.ExternalBindingService;
import io.pivotal.cfenv.boot.genai.GenaiLocator;
import io.pivotal.cfenv.core.CfCredentials;
import io.pivotal.cfenv.core.CfEnv;
import io.pivotal.cfenv.core.CfService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Configuration for Vector Store and Embedding Model.
 * Supports both local development (OpenAI/Ollama) and Cloud Foundry (GenAI) deployments.
 *
 * For Tanzu GenAI, supports both:
 * - GenAI Locator pattern (tanzu-all-models): single binding with endpoint containing config_url
 * - Direct binding pattern: individual service bindings with api_base, api_key, model_name
 */
@Configuration
@Slf4j
public class VectorStoreConfig {

    @Value("${spring.ai.openai.api-key:}")
    private String openAiApiKey;

    @Value("${app.embedding.model:text-embedding-3-small}")
    private String embeddingModelName;

    @Value("${app.embedding.dimensions:512}")
    private int embeddingDimensions;

    @Getter
    private EmbeddingModelInfo activeEmbeddingModel;

    @Autowired(required = false)
    private GenAiConfig genAiConfig;

    @Autowired(required = false)
    private ExternalBindingService externalBindingService;

    /**
     * Creates an EmbeddingModel for local development using OpenAI.
     */
    @Bean("documentEmbeddingModel")
    @Profile({"default", "local"})
    @ConditionalOnProperty(name = "spring.ai.openai.api-key")
    @Primary
    public EmbeddingModel localEmbeddingModel() {
        log.info("Creating OpenAI EmbeddingModel with model: {}, dimensions: {}",
                embeddingModelName, embeddingDimensions);

        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(openAiApiKey)
                .build();

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(embeddingModelName)
                .dimensions(embeddingDimensions)
                .build();

        activeEmbeddingModel = new EmbeddingModelInfo(embeddingModelName, "openai", "OpenAI API");
        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, options, RetryUtils.DEFAULT_RETRY_TEMPLATE);
    }

    /**
     * Creates a null EmbeddingModel for test profile to avoid auto-configuration conflicts.
     */
    @Bean("documentEmbeddingModel")
    @Profile("test")
    @Primary
    public EmbeddingModel testEmbeddingModel() {
        log.info("Test profile - no EmbeddingModel created");
        return null;
    }

    /**
     * Creates an EmbeddingModel for Cloud Foundry using GenAI service.
     * Supports both GenAI Locator pattern and direct service bindings.
     */
    @Bean("documentEmbeddingModel")
    @Profile("cloud")
    @Primary
    public EmbeddingModel cloudEmbeddingModel() {
        log.info("Creating GenAI EmbeddingModel from VCAP_SERVICES");

        // First try GenAI Locator pattern (from GenAiConfig)
        EmbeddingModel locatorModel = tryGenaiLocatorEmbedding();
        if (locatorModel != null) {
            return locatorModel;
        }

        // Fall back to direct binding pattern
        return tryDirectBindingEmbedding();
    }

    /**
     * Try to get an EmbeddingModel from GenAI Locators (both VCAP_SERVICES and external bindings).
     */
    private EmbeddingModel tryGenaiLocatorEmbedding() {
        // First check VCAP_SERVICES GenAI Locators
        if (genAiConfig != null && !genAiConfig.getGenaiLocators().isEmpty()) {
            for (GenaiLocator locator : genAiConfig.getGenaiLocators()) {
                EmbeddingModel model = tryLocatorForEmbedding(locator, "GenAI Locator (VCAP)");
                if (model != null) {
                    return model;
                }
            }
        }

        // Then check external bindings GenAI Locators
        if (externalBindingService != null && !externalBindingService.getGenaiLocators().isEmpty()) {
            for (GenaiLocator locator : externalBindingService.getGenaiLocators()) {
                EmbeddingModel model = tryLocatorForEmbedding(locator, "GenAI Locator (External)");
                if (model != null) {
                    return model;
                }
            }
        }

        // Also check if ExternalBindingService already has embedding models loaded
        if (externalBindingService != null && externalBindingService.hasAnyEmbeddingModels()) {
            List<String> embeddingNames = externalBindingService.getAvailableEmbeddingModelNames();
            if (!embeddingNames.isEmpty()) {
                String modelName = embeddingNames.get(0);
                EmbeddingModel model = externalBindingService.getEmbeddingModelByName(modelName);
                if (model != null) {
                    ExternalBindingService.ExternalModelMetadata metadata =
                            externalBindingService.getEmbeddingModelMetadata().get(modelName);
                    activeEmbeddingModel = new EmbeddingModelInfo(
                            modelName,
                            "external",
                            metadata != null ? metadata.bindingName() : "External Binding"
                    );
                    log.info("Using EmbeddingModel from External Binding: {}", modelName);
                    return model;
                }
            }
        }

        log.debug("No GenAI Locators available for embedding model");
        return null;
    }

    /**
     * Try to get an embedding model from a specific locator.
     */
    private EmbeddingModel tryLocatorForEmbedding(GenaiLocator locator, String source) {
        try {
            List<String> embeddingModelNames = locator.getModelNamesByCapability("EMBEDDING");
            log.info("{} provides {} embedding model(s): {}",
                    source, embeddingModelNames != null ? embeddingModelNames.size() : 0, embeddingModelNames);

            if (embeddingModelNames != null && !embeddingModelNames.isEmpty()) {
                String modelName = embeddingModelNames.get(0);
                EmbeddingModel embeddingModel = locator.getEmbeddingModelByName(modelName);

                if (embeddingModel != null) {
                    activeEmbeddingModel = new EmbeddingModelInfo(
                            modelName,
                            "genai-locator",
                            source
                    );
                    log.info("Using EmbeddingModel from {}: {}", source, modelName);
                    return embeddingModel;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get EmbeddingModel from {}: {}", source, e.getMessage());
        }
        return null;
    }

    /**
     * Try to get an EmbeddingModel from direct service bindings.
     */
    private EmbeddingModel tryDirectBindingEmbedding() {
        try {
            CfEnv cfEnv = new CfEnv();
            List<CfService> genaiServices = cfEnv.findServicesByLabel("genai");

            log.info("Found {} GenAI service(s) in VCAP_SERVICES for embedding", genaiServices.size());

            // Collect all potential embedding models
            List<EmbeddingCandidate> embeddingCandidates = new ArrayList<>();

            for (CfService service : genaiServices) {
                try {
                    // Skip GenAI Locator services (they're handled separately)
                    if (isGenaiLocatorService(service)) {
                        continue;
                    }

                    String serviceName = service.getName();
                    CfCredentials credentials = service.getCredentials();
                    Map<String, Object> credMap = credentials.getMap();

                    // Check for multi-model format with model_capabilities
                    Object capabilitiesObj = credMap.get("model_capabilities");
                    if (capabilitiesObj != null) {
                        List<String> capabilities = parseCapabilities(capabilitiesObj);
                        if (capabilities.contains("embedding") || capabilities.contains("embeddings")) {
                            EmbeddingCandidate candidate = createCandidate(service, credentials, 1);
                            if (candidate != null) {
                                log.info("Found embedding model via capabilities: {} (service: {})",
                                        candidate.modelName, serviceName);
                                embeddingCandidates.add(candidate);
                                continue;
                            }
                        }
                    }

                    // Check for model_type = "embedding"
                    String modelType = credentials.getString("model_type");
                    if ("embedding".equalsIgnoreCase(modelType) || "embeddings".equalsIgnoreCase(modelType)) {
                        EmbeddingCandidate candidate = createCandidate(service, credentials, 2);
                        if (candidate != null) {
                            log.info("Found embedding model via model_type: {} (service: {})",
                                    candidate.modelName, serviceName);
                            embeddingCandidates.add(candidate);
                            continue;
                        }
                    }

                    // Check for model_name containing "embed"
                    String modelName = getModelName(credentials);
                    if (modelName != null && modelName.toLowerCase().contains("embed")) {
                        EmbeddingCandidate candidate = createCandidate(service, credentials, 3);
                        if (candidate != null) {
                            log.info("Found embedding model via name pattern: {} (service: {})",
                                    candidate.modelName, serviceName);
                            embeddingCandidates.add(candidate);
                            continue;
                        }
                    }

                    // Add as fallback candidate (lowest priority)
                    EmbeddingCandidate candidate = createCandidate(service, credentials, 10);
                    if (candidate != null) {
                        embeddingCandidates.add(candidate);
                    }

                } catch (Exception e) {
                    log.warn("Failed to process GenAI service {}: {}", service.getName(), e.getMessage());
                }
            }

            // Sort by priority and select best candidate
            if (!embeddingCandidates.isEmpty()) {
                embeddingCandidates.sort((a, b) -> Integer.compare(a.priority, b.priority));
                EmbeddingCandidate selected = embeddingCandidates.get(0);

                log.info("Selected embedding model: {} (service: {}, priority: {})",
                        selected.modelName, selected.serviceName, selected.priority);

                OpenAiApi openAiApi = OpenAiApi.builder()
                        .baseUrl(selected.apiUrl)
                        .apiKey(selected.apiKey)
                        .build();

                OpenAiEmbeddingOptions.Builder optionsBuilder = OpenAiEmbeddingOptions.builder()
                        .model(selected.modelName);

                // Only set dimensions if explicitly configured and model supports it
                if (embeddingDimensions > 0 && selected.supportsDimensions) {
                    optionsBuilder.dimensions(embeddingDimensions);
                }

                activeEmbeddingModel = new EmbeddingModelInfo(
                        selected.modelName,
                        "genai",
                        selected.serviceName
                );

                return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, optionsBuilder.build(), RetryUtils.DEFAULT_RETRY_TEMPLATE);
            }

            log.warn("No suitable GenAI embedding service found in VCAP_SERVICES");
            return null;

        } catch (Exception e) {
            log.error("Failed to create GenAI EmbeddingModel: {}", e.getMessage(), e);
            return null;
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
     * Creates a PgVector store for document embeddings.
     * Uses the same PostgreSQL database as the application.
     */
    @Bean("documentVectorStore")
    @Profile("!test")
    @Primary
    public VectorStore documentVectorStore(JdbcTemplate jdbcTemplate,
                                   @Autowired(required = false) EmbeddingModel embeddingModel) {
        if (embeddingModel == null) {
            log.warn("No EmbeddingModel available - VectorStore will not be created");
            return null;
        }

        // Check if the database supports pgvector (PostgreSQL only)
        boolean isPostgres = false;
        try {
            String dbProductName = jdbcTemplate.getDataSource().getConnection().getMetaData().getDatabaseProductName();
            isPostgres = "PostgreSQL".equalsIgnoreCase(dbProductName);
            log.info("Database product: {} - pgvector support: {}", dbProductName, isPostgres);
        } catch (Exception e) {
            log.warn("Could not determine database type: {}", e.getMessage());
        }

        if (!isPostgres) {
            log.warn("Not using PostgreSQL - PgVectorStore requires PostgreSQL with pgvector extension. Document embedding disabled.");
            return null;
        }

        log.info("Creating PgVectorStore with dimensions: {}", embeddingDimensions);

        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .schemaName("public")
                .vectorTableName("document_embeddings")
                .dimensions(embeddingDimensions)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(true)
                .build();
    }

    /**
     * Parse model capabilities from various formats (list, comma-separated string, etc.)
     */
    @SuppressWarnings("unchecked")
    private List<String> parseCapabilities(Object capabilitiesObj) {
        List<String> result = new ArrayList<>();

        if (capabilitiesObj instanceof List) {
            for (Object item : (List<?>) capabilitiesObj) {
                result.add(item.toString().toLowerCase());
            }
        } else if (capabilitiesObj instanceof String) {
            String capStr = (String) capabilitiesObj;
            for (String cap : capStr.split("[,;\\s]+")) {
                if (!cap.isBlank()) {
                    result.add(cap.toLowerCase().trim());
                }
            }
        }

        return result;
    }

    /**
     * Get model name from credentials using various field names.
     */
    private String getModelName(CfCredentials credentials) {
        String modelName = credentials.getString("model_name");
        if (modelName == null) modelName = credentials.getString("model");
        if (modelName == null) modelName = credentials.getString("model_id");
        return modelName;
    }

    /**
     * Get API URL from credentials using various field names.
     */
    private String getApiUrl(CfCredentials credentials) {
        String apiUrl = credentials.getString("api_base");
        if (apiUrl == null) apiUrl = credentials.getString("api_url");
        if (apiUrl == null) apiUrl = credentials.getString("base_url");
        if (apiUrl == null) apiUrl = credentials.getString("uri");
        if (apiUrl == null) apiUrl = credentials.getString("url");
        if (apiUrl == null) apiUrl = credentials.getString("endpoint");
        return apiUrl;
    }

    /**
     * Get API key from credentials using various field names.
     */
    private String getApiKey(CfCredentials credentials) {
        String apiKey = credentials.getString("api_key");
        if (apiKey == null) apiKey = credentials.getString("apiKey");
        if (apiKey == null) apiKey = credentials.getString("access_token");
        if (apiKey == null) apiKey = credentials.getString("token");
        return apiKey;
    }

    /**
     * Create an embedding candidate from service credentials.
     */
    private EmbeddingCandidate createCandidate(CfService service, CfCredentials credentials, int priority) {
        String apiUrl = getApiUrl(credentials);
        String apiKey = getApiKey(credentials);
        String modelName = getModelName(credentials);

        if (apiUrl == null || apiKey == null) {
            log.debug("Service {} missing required credentials (url: {}, key: {})",
                    service.getName(), apiUrl != null, apiKey != null);
            return null;
        }

        // Use service name as model name if not specified
        if (modelName == null) {
            modelName = service.getName();
        }

        // Check if model supports custom dimensions
        boolean supportsDimensions = modelName.toLowerCase().contains("3-small") ||
                                     modelName.toLowerCase().contains("3-large") ||
                                     modelName.toLowerCase().contains("ada");

        return new EmbeddingCandidate(
                service.getName(),
                modelName,
                apiUrl,
                apiKey,
                priority,
                supportsDimensions
        );
    }

    /**
     * Information about an embedding model candidate.
     */
    private record EmbeddingCandidate(
            String serviceName,
            String modelName,
            String apiUrl,
            String apiKey,
            int priority,
            boolean supportsDimensions
    ) {}

    /**
     * Information about the active embedding model.
     */
    public record EmbeddingModelInfo(
            String modelName,
            String provider,
            String serviceName
    ) {}
}
