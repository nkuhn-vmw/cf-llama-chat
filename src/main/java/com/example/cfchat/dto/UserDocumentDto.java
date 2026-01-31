package com.example.cfchat.dto;

import com.example.cfchat.model.UserDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for user document information.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDocumentDto {

    private UUID id;
    private String filename;
    private String originalFilename;
    private String contentType;
    private Long fileSize;
    private Integer chunkCount;
    private UserDocument.DocumentStatus status;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
    private boolean hasStoredFile;

    public static UserDocumentDto fromEntity(UserDocument document) {
        return UserDocumentDto.builder()
                .id(document.getId())
                .filename(document.getFilename())
                .originalFilename(document.getOriginalFilename())
                .contentType(document.getContentType())
                .fileSize(document.getFileSize())
                .chunkCount(document.getChunkCount())
                .status(document.getStatus())
                .errorMessage(document.getErrorMessage())
                .createdAt(document.getCreatedAt())
                .processedAt(document.getProcessedAt())
                .hasStoredFile(document.getStoragePath() != null)
                .build();
    }
}
