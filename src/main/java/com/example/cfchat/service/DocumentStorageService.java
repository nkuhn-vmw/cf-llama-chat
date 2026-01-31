package com.example.cfchat.service;

import com.example.cfchat.model.DocumentStorageConfig;
import com.example.cfchat.repository.DocumentStorageConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for storing and retrieving documents from S3 or S3-compatible storage.
 * This service is optional - if S3 is not configured, documents are only processed
 * for embeddings and originals are not stored.
 */
@Service
@Slf4j
public class DocumentStorageService {

    private final DocumentStorageConfigRepository configRepository;
    private volatile S3Client s3Client;
    private volatile DocumentStorageConfig cachedConfig;

    public DocumentStorageService(DocumentStorageConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    /**
     * Check if S3 storage is enabled and properly configured.
     */
    public boolean isStorageEnabled() {
        return configRepository.findConfiguration()
                .map(config -> config.isEnabled() && isConfigValid(config))
                .orElse(false);
    }

    /**
     * Get the current storage configuration.
     */
    public Optional<DocumentStorageConfig> getConfiguration() {
        return configRepository.findConfiguration();
    }

    /**
     * Save or update storage configuration.
     */
    public DocumentStorageConfig saveConfiguration(DocumentStorageConfig config) {
        // Clear cached client when config changes
        this.s3Client = null;
        this.cachedConfig = null;

        // Check if configuration exists
        Optional<DocumentStorageConfig> existing = configRepository.findConfiguration();
        if (existing.isPresent()) {
            DocumentStorageConfig existingConfig = existing.get();
            existingConfig.setEnabled(config.isEnabled());
            existingConfig.setEndpointUrl(config.getEndpointUrl());
            existingConfig.setBucketName(config.getBucketName());
            existingConfig.setRegion(config.getRegion());
            existingConfig.setAccessKey(config.getAccessKey());
            existingConfig.setSecretKey(config.getSecretKey());
            existingConfig.setPathPrefix(config.getPathPrefix());
            existingConfig.setPathStyleAccess(config.isPathStyleAccess());
            return configRepository.save(existingConfig);
        } else {
            return configRepository.save(config);
        }
    }

    /**
     * Test the S3 connection with the given configuration.
     * Returns an error message if connection fails, or null if successful.
     */
    public String testConnection(DocumentStorageConfig config) {
        if (!isConfigValid(config)) {
            return "Invalid configuration: bucket name, region, access key, and secret key are required";
        }

        try {
            S3Client testClient = buildS3Client(config);
            HeadBucketRequest request = HeadBucketRequest.builder()
                    .bucket(config.getBucketName())
                    .build();
            testClient.headBucket(request);
            testClient.close();
            return null; // Success
        } catch (NoSuchBucketException e) {
            return "Bucket does not exist: " + config.getBucketName();
        } catch (S3Exception e) {
            return "S3 error: " + e.awsErrorDetails().errorMessage();
        } catch (Exception e) {
            return "Connection failed: " + e.getMessage();
        }
    }

    /**
     * Store a document in S3.
     *
     * @param userId     The user's ID
     * @param documentId The document's ID
     * @param file       The multipart file to store
     * @return The storage path (S3 key) or null if storage is not enabled
     */
    public String storeDocument(UUID userId, UUID documentId, MultipartFile file) throws IOException {
        if (!isStorageEnabled()) {
            log.debug("S3 storage not enabled, skipping document storage");
            return null;
        }

        DocumentStorageConfig config = getConfigurationOrThrow();
        S3Client client = getOrCreateS3Client();

        String key = buildStorageKey(config, userId, documentId, file.getOriginalFilename());

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(config.getBucketName())
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            log.info("Stored document {} for user {} at s3://{}/{}", documentId, userId, config.getBucketName(), key);
            return key;

        } catch (S3Exception e) {
            log.error("Failed to store document {} in S3: {}", documentId, e.awsErrorDetails().errorMessage());
            throw new IOException("Failed to store document in S3: " + e.awsErrorDetails().errorMessage(), e);
        }
    }

    /**
     * Retrieve a document from S3.
     *
     * @param storagePath The S3 key of the document
     * @return InputStream of the document contents
     */
    public InputStream getDocument(String storagePath) throws IOException {
        if (!isStorageEnabled()) {
            throw new IllegalStateException("S3 storage is not enabled");
        }

        DocumentStorageConfig config = getConfigurationOrThrow();
        S3Client client = getOrCreateS3Client();

        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(config.getBucketName())
                    .key(storagePath)
                    .build();

            return client.getObject(request);

        } catch (NoSuchKeyException e) {
            throw new IOException("Document not found in S3: " + storagePath);
        } catch (S3Exception e) {
            throw new IOException("Failed to retrieve document from S3: " + e.awsErrorDetails().errorMessage(), e);
        }
    }

    /**
     * Delete a document from S3.
     *
     * @param storagePath The S3 key of the document
     */
    public void deleteDocument(String storagePath) {
        if (!isStorageEnabled() || storagePath == null) {
            return;
        }

        DocumentStorageConfig config = getConfigurationOrThrow();
        S3Client client = getOrCreateS3Client();

        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(config.getBucketName())
                    .key(storagePath)
                    .build();

            client.deleteObject(request);
            log.info("Deleted document from S3: {}", storagePath);

        } catch (S3Exception e) {
            log.warn("Failed to delete document from S3: {} - {}", storagePath, e.awsErrorDetails().errorMessage());
        }
    }

    /**
     * Build the storage key (S3 path) for a document.
     */
    private String buildStorageKey(DocumentStorageConfig config, UUID userId, UUID documentId, String originalFilename) {
        StringBuilder keyBuilder = new StringBuilder();

        // Add path prefix if configured
        if (config.getPathPrefix() != null && !config.getPathPrefix().isBlank()) {
            String prefix = config.getPathPrefix().trim();
            if (!prefix.endsWith("/")) {
                prefix += "/";
            }
            keyBuilder.append(prefix);
        }

        // Organize by user ID
        keyBuilder.append("users/")
                .append(userId.toString())
                .append("/")
                .append(documentId.toString());

        // Add original filename extension if present
        if (originalFilename != null && originalFilename.contains(".")) {
            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            keyBuilder.append(extension);
        }

        return keyBuilder.toString();
    }

    /**
     * Validate that the configuration has all required fields.
     */
    private boolean isConfigValid(DocumentStorageConfig config) {
        return config != null &&
                config.getBucketName() != null && !config.getBucketName().isBlank() &&
                config.getRegion() != null && !config.getRegion().isBlank() &&
                config.getAccessKey() != null && !config.getAccessKey().isBlank() &&
                config.getSecretKey() != null && !config.getSecretKey().isBlank();
    }

    /**
     * Get existing configuration or throw exception.
     */
    private DocumentStorageConfig getConfigurationOrThrow() {
        return configRepository.findConfiguration()
                .orElseThrow(() -> new IllegalStateException("No storage configuration found"));
    }

    /**
     * Get or create the S3 client with caching.
     */
    private synchronized S3Client getOrCreateS3Client() {
        DocumentStorageConfig config = getConfigurationOrThrow();

        // Check if cached client is still valid for current config
        if (s3Client != null && cachedConfig != null &&
                cachedConfig.getUpdatedAt() != null &&
                config.getUpdatedAt() != null &&
                cachedConfig.getUpdatedAt().equals(config.getUpdatedAt())) {
            return s3Client;
        }

        // Build new client
        s3Client = buildS3Client(config);
        cachedConfig = config;
        return s3Client;
    }

    /**
     * Build an S3 client from configuration.
     */
    private S3Client buildS3Client(DocumentStorageConfig config) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                config.getAccessKey(),
                config.getSecretKey()
        );

        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(config.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials));

        // Custom endpoint for S3-compatible services (MinIO, etc.)
        if (config.getEndpointUrl() != null && !config.getEndpointUrl().isBlank()) {
            builder.endpointOverride(URI.create(config.getEndpointUrl()));
        }

        // Path-style access for S3-compatible services
        if (config.isPathStyleAccess()) {
            builder.serviceConfiguration(S3Configuration.builder()
                    .pathStyleAccessEnabled(true)
                    .build());
        }

        return builder.build();
    }
}
