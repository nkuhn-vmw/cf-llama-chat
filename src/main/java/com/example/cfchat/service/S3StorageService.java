package com.example.cfchat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@ConditionalOnProperty(name = "storage.provider", havingValue = "s3")
public class S3StorageService implements StorageService {

    @Override
    public String store(String key, byte[] data, String contentType) {
        // TODO: Implement S3 storage
        throw new UnsupportedOperationException("S3 storage not yet implemented");
    }

    @Override
    public byte[] retrieve(String key) {
        // TODO: Implement S3 retrieval
        throw new UnsupportedOperationException("S3 storage not yet implemented");
    }

    @Override
    public void delete(String key) {
        // TODO: Implement S3 deletion
        log.warn("S3 storage delete not yet implemented for key: {}", key);
    }
}
