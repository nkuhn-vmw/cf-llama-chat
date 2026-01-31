package com.example.cfchat.config;

import io.pivotal.cfenv.core.CfCredentials;
import io.pivotal.cfenv.core.CfEnv;
import io.pivotal.cfenv.core.CfService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * Configuration for Vector Store and Embedding Model.
 * Supports both local development (OpenAI/Ollama) and Cloud Foundry (GenAI) deployments.
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

    /**
     * Creates an EmbeddingModel for local development using OpenAI.
     */
    @Bean("documentEmbeddingModel")
    @Profile({"default", "local"})
    @ConditionalOnProperty(name = "spring.ai.openai.api-key")
    @Primary
    public EmbeddingModel localEmbeddingModel() {
        log.info("Creating OpenAI EmbeddingModel with model: {}", embeddingModelName);

        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(openAiApiKey)
                .build();

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(embeddingModelName)
                .dimensions(embeddingDimensions)
                .build();

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
     * Looks for a GenAI service with embedding capabilities in VCAP_SERVICES.
     */
    @Bean("documentEmbeddingModel")
    @Profile("cloud")
    @Primary
    public EmbeddingModel cloudEmbeddingModel() {
        log.info("Creating GenAI EmbeddingModel from VCAP_SERVICES");

        try {
            CfEnv cfEnv = new CfEnv();

            // Look for GenAI services that support embeddings
            List<CfService> genaiServices = cfEnv.findServicesByLabel("genai");

            for (CfService service : genaiServices) {
                CfCredentials credentials = service.getCredentials();
                String modelName = credentials.getString("model_name");

                // Check if this is an embedding model (by naming convention or explicit flag)
                if (modelName != null && (modelName.contains("embed") ||
                        "embedding".equals(credentials.getString("model_type")))) {

                    String apiUrl = credentials.getString("api_base");
                    if (apiUrl == null) apiUrl = credentials.getString("uri");
                    if (apiUrl == null) apiUrl = credentials.getString("url");

                    String apiKey = credentials.getString("api_key");

                    if (apiUrl != null && apiKey != null) {
                        log.info("Found GenAI embedding service: {} (model: {})",
                                service.getName(), modelName);

                        OpenAiApi openAiApi = OpenAiApi.builder()
                                .baseUrl(apiUrl)
                                .apiKey(apiKey)
                                .build();

                        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                                .model(modelName)
                                .build();

                        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, options, RetryUtils.DEFAULT_RETRY_TEMPLATE);
                    }
                }
            }

            // Fallback: Use the first GenAI service as embedding model
            // (some services support both chat and embedding)
            if (!genaiServices.isEmpty()) {
                CfService service = genaiServices.get(0);
                CfCredentials credentials = service.getCredentials();

                String apiUrl = credentials.getString("api_base");
                if (apiUrl == null) apiUrl = credentials.getString("uri");
                if (apiUrl == null) apiUrl = credentials.getString("url");

                String apiKey = credentials.getString("api_key");
                String modelName = credentials.getString("model_name");
                if (modelName == null) modelName = credentials.getString("model");

                if (apiUrl != null && apiKey != null) {
                    log.info("Using GenAI service as embedding model: {} (model: {})",
                            service.getName(), modelName);

                    OpenAiApi openAiApi = OpenAiApi.builder()
                            .baseUrl(apiUrl)
                            .apiKey(apiKey)
                            .build();

                    // Use text-embedding-3-small as default embedding model name
                    // as the chat model might not support embeddings directly
                    OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                            .model("text-embedding-3-small")
                            .build();

                    return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, options, RetryUtils.DEFAULT_RETRY_TEMPLATE);
                }
            }

            log.warn("No suitable GenAI embedding service found in VCAP_SERVICES");
            return null;

        } catch (Exception e) {
            log.error("Failed to create GenAI EmbeddingModel: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Creates a PgVector store for document embeddings.
     * Uses the same PostgreSQL database as the application.
     */
    @Bean
    @Profile("!test")
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate,
                                   @org.springframework.beans.factory.annotation.Autowired(required = false) EmbeddingModel embeddingModel) {
        if (embeddingModel == null) {
            log.warn("No EmbeddingModel available - VectorStore will not be created");
            return null;
        }

        log.info("Creating PgVectorStore with embedding model: {}",
                embeddingModel.getClass().getSimpleName());

        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .schemaName("public")
                .vectorTableName("document_embeddings")
                .dimensions(embeddingDimensions)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(true)
                .build();
    }
}
