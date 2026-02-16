package com.example.cfchat.dto;

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
public class ChatRequest {

    private UUID conversationId;

    @NotBlank(message = "Message content is required")
    @Size(max = 100000, message = "Message must not exceed 100,000 characters")
    private String message;

    private String provider;

    private String model;

    private UUID skillId;

    /**
     * Whether to use the user's uploaded documents for RAG context.
     * When true, relevant document chunks will be included in the prompt.
     */
    @Builder.Default
    private boolean useDocumentContext = false;

    /**
     * Whether to use MCP tools during chat.
     * When true, available MCP tools will be provided to the model for function calling.
     */
    @Builder.Default
    private boolean useTools = true;

    /**
     * Whether this is a temporary (ephemeral) chat.
     * When true, messages are not persisted to the database.
     * The conversation exists only for the duration of the session.
     */
    @Builder.Default
    private boolean temporary = false;
}
