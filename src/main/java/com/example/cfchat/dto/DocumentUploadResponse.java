package com.example.cfchat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response DTO for document upload operations.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentUploadResponse {

    private UUID documentId;
    private String filename;
    private String status;
    private String message;
    private Integer chunkCount;
    private Long processingTimeMs;
}
