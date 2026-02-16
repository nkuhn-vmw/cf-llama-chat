package com.example.cfchat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@ConditionalOnProperty(name = "storage.provider", havingValue = "azure")
public class AzureBlobStorageService implements StorageService {

    @Override
    public String store(String key, byte[] data, String contentType) {
        // TODO: Implement Azure Blob storage
        throw new UnsupportedOperationException("Azure Blob storage not yet implemented");
    }

    @Override
    public byte[] retrieve(String key) {
        // TODO: Implement Azure Blob retrieval
        throw new UnsupportedOperationException("Azure Blob storage not yet implemented");
    }

    @Override
    public void delete(String key) {
        // TODO: Implement Azure Blob deletion
        log.warn("Azure Blob storage delete not yet implemented for key: {}", key);
    }
}
