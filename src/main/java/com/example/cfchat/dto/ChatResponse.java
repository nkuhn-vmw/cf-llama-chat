package com.example.cfchat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatResponse {
    private UUID conversationId;
    private UUID messageId;
    private String content;
    private String htmlContent;
    private String model;
    private boolean streaming;
    private boolean complete;
}
