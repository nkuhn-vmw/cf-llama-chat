package com.example.cfchat.service;

import com.example.cfchat.model.ExternalBinding;
import com.example.cfchat.repository.ExternalBindingRepository;
import io.pivotal.cfenv.boot.genai.DefaultGenaiLocator;
import io.pivotal.cfenv.boot.genai.GenaiLocator;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ExternalBindingService {

    private final ExternalBindingRepository repository;
    private final RestClient.Builder restClientBuilder;

    // Thread-safe storage for loaded chat models
    private final Map<String, ChatModel> loadedModels = new ConcurrentHashMap<>();

    // Thread-safe storage for loaded embedding models
    private final Map<String, EmbeddingModel> loadedEmbeddingModels = new ConcurrentHashMap<>();

    // Track which models belong to which binding
    private final Map<UUID, Set<String>> bindingModelNames = new ConcurrentHashMap<>();

    // Track which embedding models belong to which binding
    private final Map<UUID, Set<String>> bindingEmbeddingModelNames = new ConcurrentHashMap<>();

    // Store GenaiLocators for bindings that have config_url
    @Getter
    private final List<GenaiLocator> genaiLocators = new ArrayList<>();

    // Track model metadata for display
    @Getter
    private final Map<String, ExternalModelMetadata> modelMetadata = new ConcurrentHashMap<>();

    // Track embedding model metadata
    @Getter
    private final Map<String, ExternalModelMetadata> embeddingModelMetadata = new ConcurrentHashMap<>();

    public ExternalBindingService(ExternalBindingRepository repository) {
        this.repository = repository;
        this.restClientBuilder = RestClient.builder();
    }

    @PostConstruct
    public void init() {
        log.info("ExternalBindingService initializing - loading enabled bindings");
        loadAllEnabledBindings();
    }

    /**
     * Load all enabled bindings at startup.
     */
    public void loadAllEnabledBindings() {
        List<ExternalBinding> enabledBindings = repository.findByEnabled(true);
        log.info("Found {} enabled external binding(s)", enabledBindings.size());

        for (ExternalBinding binding : enabledBindings) {
            try {
                loadModelsFromBinding(binding);
            } catch (Exception e) {
                log.warn("Failed to load models from binding {}: {}", binding.getName(), e.getMessage());
            }
        }

        log.info("Total external models loaded: {}", loadedModels.size());
    }

    /**
     * Load models from a specific binding.
     */
    public int loadModelsFromBinding(ExternalBinding binding) {
        log.info("Loading models from external binding: {} (configUrl: {})",
                binding.getName(), binding.getConfigUrl() != null ? "yes" : "no");

        Set<String> modelNames = new HashSet<>();

        try {
            if (binding.getConfigUrl() != null && !binding.getConfigUrl().isBlank()) {
                // Use GenaiLocator for dynamic model discovery
                loadModelsViaLocator(binding, modelNames);
            } else {
                // Direct OpenAI-compatible API without discovery
                loadDirectModel(binding, modelNames);
            }

            bindingModelNames.put(binding.getId(), modelNames);
            log.info("Loaded {} model(s) from binding {}: {}", modelNames.size(), binding.getName(), modelNames);
            return modelNames.size();

        } catch (Exception e) {
            log.error("Failed to load models from binding {}: {}", binding.getName(), e.getMessage(), e);
            bindingModelNames.put(binding.getId(), modelNames);
            return 0;
        }
    }

    private void loadModelsViaLocator(ExternalBinding binding, Set<String> modelNames) {
        GenaiLocator locator = new DefaultGenaiLocator(
                restClientBuilder,
                binding.getConfigUrl(),
                binding.getApiKey(),
                binding.getApiBase()
        );

        // Store the locator for potential use by other components
        genaiLocators.add(locator);

        // Load chat models
        List<String> chatModelNames = locator.getModelNamesByCapability("CHAT");
        log.info("GenAI Locator for {} provides {} chat model(s): {}",
                binding.getName(), chatModelNames != null ? chatModelNames.size() : 0, chatModelNames);

        if (chatModelNames != null && !chatModelNames.isEmpty()) {
            for (String modelName : chatModelNames) {
                // Skip embedding models from chat list
                if (isEmbeddingModel(modelName)) {
                    log.debug("Skipping embedding model from chat: {}", modelName);
                    continue;
                }

                try {
                    ChatModel chatModel = locator.getChatModelByName(modelName);
                    if (chatModel != null) {
                        loadedModels.put(modelName, chatModel);
                        modelNames.add(modelName);
                        modelMetadata.put(modelName, new ExternalModelMetadata(
                                modelName,
                                binding.getName(),
                                binding.getId(),
                                "GenaiLocator"
                        ));
                        log.info("Registered external chat model via Locator: {} (binding: {})", modelName, binding.getName());
                    }
                } catch (Exception e) {
                    log.warn("Failed to get ChatModel for {} from binding {}: {}",
                            modelName, binding.getName(), e.getMessage());
                }
            }
        }

        // Load embedding models
        Set<String> embeddingNames = new HashSet<>();
        List<String> embeddingModelNames = locator.getModelNamesByCapability("EMBEDDING");
        log.info("GenAI Locator for {} provides {} embedding model(s): {}",
                binding.getName(), embeddingModelNames != null ? embeddingModelNames.size() : 0, embeddingModelNames);

        if (embeddingModelNames != null && !embeddingModelNames.isEmpty()) {
            for (String modelName : embeddingModelNames) {
                try {
                    EmbeddingModel embeddingModel = locator.getEmbeddingModelByName(modelName);
                    if (embeddingModel != null) {
                        loadedEmbeddingModels.put(modelName, embeddingModel);
                        embeddingNames.add(modelName);
                        embeddingModelMetadata.put(modelName, new ExternalModelMetadata(
                                modelName,
                                binding.getName(),
                                binding.getId(),
                                "GenaiLocator"
                        ));
                        log.info("Registered external embedding model via Locator: {} (binding: {})", modelName, binding.getName());
                    }
                } catch (Exception e) {
                    log.warn("Failed to get EmbeddingModel for {} from binding {}: {}",
                            modelName, binding.getName(), e.getMessage());
                }
            }
        }
        bindingEmbeddingModelNames.put(binding.getId(), embeddingNames);
    }

    private void loadDirectModel(ExternalBinding binding, Set<String> modelNames) {
        // Create a direct OpenAI-compatible model
        // Use binding name as model name since we don't have discovery
        String modelName = binding.getName();

        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(binding.getApiBase())
                .apiKey(binding.getApiKey())
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(modelName)
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();

        loadedModels.put(modelName, chatModel);
        modelNames.add(modelName);
        modelMetadata.put(modelName, new ExternalModelMetadata(
                modelName,
                binding.getName(),
                binding.getId(),
                "OpenAiChatModel"
        ));

        log.info("Registered external model via direct API: {} (binding: {})", modelName, binding.getName());
    }

    /**
     * Unload all models from a specific binding.
     */
    public void unloadModelsFromBinding(UUID bindingId) {
        // Unload chat models
        Set<String> models = bindingModelNames.remove(bindingId);
        if (models != null) {
            for (String modelName : models) {
                loadedModels.remove(modelName);
                modelMetadata.remove(modelName);
                log.info("Unloaded external chat model: {}", modelName);
            }
        }

        // Unload embedding models
        Set<String> embeddingModels = bindingEmbeddingModelNames.remove(bindingId);
        if (embeddingModels != null) {
            for (String modelName : embeddingModels) {
                loadedEmbeddingModels.remove(modelName);
                embeddingModelMetadata.remove(modelName);
                log.info("Unloaded external embedding model: {}", modelName);
            }
        }
    }

    /**
     * Reload models from a binding (unload then load).
     */
    public int reloadModelsFromBinding(ExternalBinding binding) {
        unloadModelsFromBinding(binding.getId());
        return loadModelsFromBinding(binding);
    }

    /**
     * Get a ChatModel by its model name.
     */
    public ChatModel getChatModelByName(String modelName) {
        return loadedModels.get(modelName);
    }

    /**
     * Check if a model exists in external bindings.
     */
    public boolean hasModel(String modelName) {
        return loadedModels.containsKey(modelName);
    }

    /**
     * Get all available external model names.
     */
    public List<String> getAvailableModelNames() {
        return new ArrayList<>(loadedModels.keySet());
    }

    /**
     * Get an EmbeddingModel by its model name.
     */
    public EmbeddingModel getEmbeddingModelByName(String modelName) {
        return loadedEmbeddingModels.get(modelName);
    }

    /**
     * Check if an embedding model exists in external bindings.
     */
    public boolean hasEmbeddingModel(String modelName) {
        return loadedEmbeddingModels.containsKey(modelName);
    }

    /**
     * Get all available external embedding model names.
     */
    public List<String> getAvailableEmbeddingModelNames() {
        return new ArrayList<>(loadedEmbeddingModels.keySet());
    }

    /**
     * Get the first available embedding model, or null if none available.
     */
    public EmbeddingModel getFirstAvailableEmbeddingModel() {
        if (loadedEmbeddingModels.isEmpty()) {
            return null;
        }
        return loadedEmbeddingModels.values().iterator().next();
    }

    /**
     * Check if any embedding models are available from external bindings.
     */
    public boolean hasAnyEmbeddingModels() {
        return !loadedEmbeddingModels.isEmpty();
    }

    /**
     * Get the count of loaded models for a specific binding.
     */
    public int getModelCountForBinding(UUID bindingId) {
        Set<String> models = bindingModelNames.get(bindingId);
        return models != null ? models.size() : 0;
    }

    /**
     * Get model names for a specific binding.
     */
    public Set<String> getModelNamesForBinding(UUID bindingId) {
        return bindingModelNames.getOrDefault(bindingId, Collections.emptySet());
    }

    // CRUD operations

    public List<ExternalBinding> findAll() {
        return repository.findAll();
    }

    public Optional<ExternalBinding> findById(UUID id) {
        return repository.findById(id);
    }

    public Optional<ExternalBinding> findByName(String name) {
        return repository.findByName(name);
    }

    public boolean existsByName(String name) {
        return repository.existsByName(name);
    }

    public ExternalBinding save(ExternalBinding binding) {
        return repository.save(binding);
    }

    public ExternalBinding create(ExternalBinding binding) {
        if (existsByName(binding.getName())) {
            throw new IllegalArgumentException("Binding with name '" + binding.getName() + "' already exists");
        }
        ExternalBinding saved = repository.save(binding);

        // Auto-load if enabled
        if (saved.isEnabled()) {
            loadModelsFromBinding(saved);
        }

        return saved;
    }

    public ExternalBinding update(ExternalBinding binding) {
        ExternalBinding saved = repository.save(binding);

        // Reload models if enabled, unload if disabled
        if (saved.isEnabled()) {
            reloadModelsFromBinding(saved);
        } else {
            unloadModelsFromBinding(saved.getId());
        }

        return saved;
    }

    public void delete(UUID id) {
        unloadModelsFromBinding(id);
        repository.deleteById(id);
    }

    public ExternalBinding setEnabled(UUID id, boolean enabled) {
        ExternalBinding binding = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Binding not found: " + id));

        binding.setEnabled(enabled);
        ExternalBinding saved = repository.save(binding);

        if (enabled) {
            loadModelsFromBinding(saved);
        } else {
            unloadModelsFromBinding(id);
        }

        return saved;
    }

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
     * Metadata about an external model.
     */
    public record ExternalModelMetadata(
            String modelName,
            String bindingName,
            UUID bindingId,
            String modelType
    ) {}
}
