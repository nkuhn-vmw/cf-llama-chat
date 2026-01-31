package com.example.cfchat.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity for storing S3/object storage configuration.
 * This is a singleton configuration - only one record should exist.
 * Admins can configure S3 storage through the admin portal.
 */
@Entity
@Table(name = "document_storage_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentStorageConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Whether S3 storage is enabled. If false, documents are only processed
     * for embeddings and originals are not stored.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = false;

    /**
     * The S3-compatible endpoint URL (e.g., https://s3.amazonaws.com or MinIO URL).
     */
    @Column(name = "endpoint_url")
    private String endpointUrl;

    /**
     * The S3 bucket name.
     */
    @Column(name = "bucket_name")
    private String bucketName;

    /**
     * AWS region (e.g., us-east-1).
     */
    private String region;

    /**
     * Access key ID for S3 authentication.
     */
    @Column(name = "access_key")
    private String accessKey;

    /**
     * Secret access key for S3 authentication.
     * In production, consider using encryption at rest.
     */
    @Column(name = "secret_key")
    private String secretKey;

    /**
     * Optional path prefix for organizing documents in the bucket.
     */
    @Column(name = "path_prefix")
    private String pathPrefix;

    /**
     * Whether to use path-style access (required for some S3-compatible services like MinIO).
     */
    @Column(name = "path_style_access")
    @Builder.Default
    private boolean pathStyleAccess = false;

    /**
     * Timestamp when the configuration was created.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when the configuration was last updated.
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
