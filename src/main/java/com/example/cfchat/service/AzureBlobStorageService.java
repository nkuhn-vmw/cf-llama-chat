package com.example.cfchat.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobStorageException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

@Service
@Slf4j
@ConditionalOnProperty(name = "storage.provider", havingValue = "azure")
public class AzureBlobStorageService implements StorageService {

    @Value("${storage.azure.connection-string}") private String connectionString;
    @Value("${storage.azure.container}") private String containerName;
    @Value("${storage.azure.prefix:}") private String prefix;

    private BlobContainerClient containerClient;

    @PostConstruct
    public void init() {
        log.info("Initializing Azure Blob storage service with container={}", containerName);

        try {
            BlobServiceClient serviceClient = new BlobServiceClientBuilder()
                    .connectionString(connectionString)
                    .buildClient();

            containerClient = serviceClient.getBlobContainerClient(containerName);

            // Create container if it does not exist
            if (!containerClient.exists()) {
                containerClient.create();
                log.info("Created Azure Blob container: {}", containerName);
            }

            log.info("Azure Blob storage service initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize Azure Blob storage service: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize Azure Blob storage", e);
        }
    }

    @Override
    public String store(String key, byte[] data, String contentType) {
        String blobName = buildBlobName(key);
        try {
            BlobClient blobClient = containerClient.getBlobClient(blobName);

            BlobHttpHeaders headers = new BlobHttpHeaders();
            if (contentType != null && !contentType.isBlank()) {
                headers.setContentType(contentType);
            }

            blobClient.upload(new ByteArrayInputStream(data), data.length, true);
            blobClient.setHttpHeaders(headers);

            log.debug("Stored blob in Azure: {}/{}", containerName, blobName);
            return key;
        } catch (BlobStorageException e) {
            log.error("Failed to store blob in Azure: {}/{} - {} (status={})",
                    containerName, blobName, e.getMessage(), e.getStatusCode());
            throw new RuntimeException("Failed to store file in Azure Blob: " + key, e);
        }
    }

    @Override
    public byte[] retrieve(String key) {
        String blobName = buildBlobName(key);
        try {
            BlobClient blobClient = containerClient.getBlobClient(blobName);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            blobClient.downloadStream(outputStream);

            byte[] data = outputStream.toByteArray();
            log.debug("Retrieved blob from Azure: {}/{} ({} bytes)", containerName, blobName, data.length);
            return data;
        } catch (BlobStorageException e) {
            if (e.getStatusCode() == 404) {
                log.error("Blob not found in Azure: {}/{}", containerName, blobName);
                throw new RuntimeException("File not found in Azure Blob: " + key, e);
            }
            log.error("Failed to retrieve blob from Azure: {}/{} - {} (status={})",
                    containerName, blobName, e.getMessage(), e.getStatusCode());
            throw new RuntimeException("Failed to retrieve file from Azure Blob: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        String blobName = buildBlobName(key);
        try {
            BlobClient blobClient = containerClient.getBlobClient(blobName);
            blobClient.deleteIfExists();
            log.debug("Deleted blob from Azure: {}/{}", containerName, blobName);
        } catch (BlobStorageException e) {
            log.warn("Failed to delete blob from Azure: {}/{} - {} (status={})",
                    containerName, blobName, e.getMessage(), e.getStatusCode());
        }
    }

    /**
     * Build the full blob name by prepending the configured prefix.
     */
    private String buildBlobName(String key) {
        if (prefix != null && !prefix.isBlank()) {
            String normalizedPrefix = prefix.endsWith("/") ? prefix : prefix + "/";
            return normalizedPrefix + key;
        }
        return key;
    }
}
