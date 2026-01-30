package com.example.cfchat.dto;

import jakarta.validation.constraints.NotBlank;
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
    private String message;

    private String provider;

    private String model;

    private UUID skillId;
}
