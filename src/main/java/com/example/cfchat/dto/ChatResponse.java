package com.example.cfchat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ChatResponse {
    private UUID conversationId;
    private UUID messageId;
    private String content;
    private String htmlContent;
    private String model;
    private String error;
    private boolean streaming;
    private boolean complete;
    private boolean temporary;

    // Performance metrics
    private Long timeToFirstTokenMs;
    private Double tokensPerSecond;
    private Long totalResponseTimeMs;
}
