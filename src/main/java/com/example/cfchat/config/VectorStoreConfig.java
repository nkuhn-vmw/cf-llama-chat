package com.example.cfchat.config;

import io.pivotal.cfenv.core.CfCredentials;
import io.pivotal.cfenv.core.CfEnv;
import io.pivotal.cfenv.core.CfService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for Vector Store and Embedding Model.
 * Supports both local development (OpenAI/Ollama) and Cloud Foundry (GenAI) deployments.
 *
 * For Tanzu GenAI, supports both single-model and multi-model binding formats:
 * - Single model: One service instance per model
 * - Multi-model: Multiple models exposed through a single service with model_capabilities
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

    /**
     * Creates an EmbeddingModel for local development using OpenAI.
     */
    @Bean
    @Profile("!cloud")
    @ConditionalOnProperty(name = "spring.ai.openai.api-key")
    public EmbeddingModel openAiEmbeddingModel() {
        log.info("Creating OpenAI EmbeddingModel with model: {}, dimensions: {}",
                embeddingModelName, embeddingDimensions);

        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(openAiApiKey)
                .build();

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(embeddingModelName)
                .dimensions(embeddingDimensions)
                .build();

        EmbeddingModel model = OpenAiEmbeddingModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();

        activeEmbeddingModel = new EmbeddingModelInfo(embeddingModelName, "openai", "OpenAI API");
        return model;
    }

    /**
     * Creates an EmbeddingModel for Cloud Foundry using GenAI service.
     * Supports both single-model and multi-model Tanzu GenAI binding formats.
     *
     * Looks for embedding models in the following order:
     * 1. Services with model_capabilities containing "embedding"
     * 2. Services with model_type = "embedding"
     * 3. Services with model_name containing "embed"
     * 4. Fallback to first available GenAI service
     */
    @Bean
    @Profile("cloud")
    @Primary
    public EmbeddingModel genAiEmbeddingModel() {
        log.info("Creating GenAI EmbeddingModel from VCAP_SERVICES");

        try {
            CfEnv cfEnv = new CfEnv();
            List<CfService> genaiServices = cfEnv.findServicesByLabel("genai");

            log.info("Found {} GenAI service(s) in VCAP_SERVICES", genaiServices.size());

            // Collect all potential embedding models
            List<EmbeddingCandidate> embeddingCandidates = new ArrayList<>();

            for (CfService service : genaiServices) {
                try {
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

                EmbeddingModel model = OpenAiEmbeddingModel.builder()
                        .openAiApi(openAiApi)
                        .defaultOptions(optionsBuilder.build())
                        .build();

                activeEmbeddingModel = new EmbeddingModelInfo(
                        selected.modelName,
                        "genai",
                        selected.serviceName
                );

                return model;
            }

            log.warn("No suitable GenAI embedding service found in VCAP_SERVICES");
            return null;

        } catch (Exception e) {
            log.error("Failed to create GenAI EmbeddingModel: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Creates a PgVector store for document embeddings.
     * Uses the same PostgreSQL database as the application.
     */
    @Bean
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate,
                                   EmbeddingModel embeddingModel) {
        if (embeddingModel == null) {
            log.warn("No EmbeddingModel available - VectorStore will not be created");
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
