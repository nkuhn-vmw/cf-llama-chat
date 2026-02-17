package com.example.cfchat.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgenticSearchRequest {

    @NotBlank(message = "Query is required")
    @Size(max = 10000, message = "Query must not exceed 10,000 characters")
    private String query;

    /**
     * Maximum number of search iterations (sub-query decomposition rounds).
     * Each iteration can produce multiple sub-queries.
     */
    @Builder.Default
    @Min(1)
    @Max(5)
    private int maxIterations = 3;

    /**
     * Maximum number of sub-queries per iteration.
     */
    @Builder.Default
    @Min(1)
    @Max(10)
    private int maxSubQueries = 3;

    /**
     * Number of top results to retrieve per sub-query from the vector store.
     */
    @Builder.Default
    @Min(1)
    @Max(20)
    private int topK = 5;

    /**
     * Whether to include web search results alongside vector store results.
     */
    @Builder.Default
    private boolean includeWebSearch = false;

    /**
     * The model to use for query decomposition and synthesis.
     * If null, the default model is used.
     */
    private String model;

    /**
     * The provider for the model (openai, ollama, genai, external).
     */
    private String provider;

    /**
     * The conversation ID to associate results with (optional).
     */
    private UUID conversationId;
}
