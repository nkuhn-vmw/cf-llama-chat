package com.example.cfchat.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a document uploaded by a user for embedding and RAG queries.
 * The actual document content is chunked and stored in the vector store,
 * while this entity tracks metadata about the document.
 */
@Entity
@Table(name = "user_documents", indexes = {
        @Index(name = "idx_user_documents_user_id", columnList = "user_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String filename;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "chunk_count")
    @Builder.Default
    private Integer chunkCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DocumentStatus status = DocumentStatus.PENDING;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * The S3 storage path (key) for the original document.
     * Null if S3 storage is not enabled or document was uploaded before S3 was configured.
     */
    @Column(name = "storage_path")
    private String storagePath;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum DocumentStatus {
        PENDING,      // Document uploaded, waiting to be processed
        PROCESSING,   // Currently being chunked and embedded
        COMPLETED,    // Successfully processed and embedded
        FAILED        // Processing failed
    }
}
