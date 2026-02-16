package com.example.cfchat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@ConditionalOnProperty(name = "storage.provider", havingValue = "gcs")
public class GcsStorageService implements StorageService {

    @Override
    public String store(String key, byte[] data, String contentType) {
        // TODO: Implement GCS storage
        throw new UnsupportedOperationException("GCS storage not yet implemented");
    }

    @Override
    public byte[] retrieve(String key) {
        // TODO: Implement GCS retrieval
        throw new UnsupportedOperationException("GCS storage not yet implemented");
    }

    @Override
    public void delete(String key) {
        // TODO: Implement GCS deletion
        log.warn("GCS storage delete not yet implemented for key: {}", key);
    }
}
