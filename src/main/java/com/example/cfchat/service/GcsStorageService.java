package com.example.cfchat.service;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import com.google.auth.oauth2.ServiceAccountCredentials;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;

@Service
@Slf4j
@ConditionalOnProperty(name = "storage.provider", havingValue = "gcs")
public class GcsStorageService implements StorageService {

    @Value("${storage.gcs.bucket}") private String bucket;
    @Value("${storage.gcs.project-id:}") private String projectId;
    @Value("${storage.gcs.credentials-path:}") private String credentialsPath;
    @Value("${storage.gcs.prefix:}") private String prefix;

    private Storage storage;

    @PostConstruct
    public void init() {
        log.info("Initializing GCS storage service with bucket={}, project={}", bucket, projectId);

        try {
            StorageOptions.Builder builder = StorageOptions.newBuilder();

            if (projectId != null && !projectId.isBlank()) {
                builder.setProjectId(projectId);
            }

            // Use explicit credentials file if provided, otherwise fall back to
            // Application Default Credentials (ADC) which covers GOOGLE_APPLICATION_CREDENTIALS
            // env var, GCE metadata service, etc.
            if (credentialsPath != null && !credentialsPath.isBlank()) {
                builder.setCredentials(ServiceAccountCredentials.fromStream(
                        new FileInputStream(credentialsPath)));
            }

            storage = builder.build().getService();
            log.info("GCS storage service initialized successfully");
        } catch (IOException e) {
            log.error("Failed to initialize GCS storage service: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize GCS storage", e);
        }
    }

    @Override
    public String store(String key, byte[] data, String contentType) {
        String objectName = buildObjectName(key);
        try {
            BlobId blobId = BlobId.of(bucket, objectName);
            BlobInfo.Builder blobInfoBuilder = BlobInfo.newBuilder(blobId);

            if (contentType != null && !contentType.isBlank()) {
                blobInfoBuilder.setContentType(contentType);
            }

            storage.create(blobInfoBuilder.build(), data);
            log.debug("Stored object in GCS: gs://{}/{}", bucket, objectName);
            return key;
        } catch (StorageException e) {
            log.error("Failed to store object in GCS: gs://{}/{} - {} (code={})",
                    bucket, objectName, e.getMessage(), e.getCode());
            throw new RuntimeException("Failed to store file in GCS: " + key, e);
        }
    }

    @Override
    public byte[] retrieve(String key) {
        String objectName = buildObjectName(key);
        try {
            Blob blob = storage.get(BlobId.of(bucket, objectName));
            if (blob == null || !blob.exists()) {
                log.error("Object not found in GCS: gs://{}/{}", bucket, objectName);
                throw new RuntimeException("File not found in GCS: " + key);
            }

            byte[] data = blob.getContent();
            log.debug("Retrieved object from GCS: gs://{}/{} ({} bytes)", bucket, objectName, data.length);
            return data;
        } catch (StorageException e) {
            log.error("Failed to retrieve object from GCS: gs://{}/{} - {} (code={})",
                    bucket, objectName, e.getMessage(), e.getCode());
            throw new RuntimeException("Failed to retrieve file from GCS: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        String objectName = buildObjectName(key);
        try {
            boolean deleted = storage.delete(BlobId.of(bucket, objectName));
            if (deleted) {
                log.debug("Deleted object from GCS: gs://{}/{}", bucket, objectName);
            } else {
                log.debug("Object did not exist in GCS (no-op delete): gs://{}/{}", bucket, objectName);
            }
        } catch (StorageException e) {
            log.warn("Failed to delete object from GCS: gs://{}/{} - {} (code={})",
                    bucket, objectName, e.getMessage(), e.getCode());
        }
    }

    /**
     * Build the full GCS object name by prepending the configured prefix.
     */
    private String buildObjectName(String key) {
        if (prefix != null && !prefix.isBlank()) {
            String normalizedPrefix = prefix.endsWith("/") ? prefix : prefix + "/";
            return normalizedPrefix + key;
        }
        return key;
    }
}
