package com.example.cfchat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;

@Service
@Slf4j
@ConditionalOnProperty(name = "storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {
    @Value("${storage.local.path:/tmp/cf-llama-uploads}") private String storagePath;

    @Override
    public String store(String key, byte[] data, String contentType) {
        try {
            Path path = Path.of(storagePath, key);
            Files.createDirectories(path.getParent());
            Files.write(path, data);
            return key;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + key, e);
        }
    }

    @Override
    public byte[] retrieve(String key) {
        try {
            return Files.readAllBytes(Path.of(storagePath, key));
        } catch (IOException e) {
            throw new RuntimeException("Failed to retrieve file: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(Path.of(storagePath, key));
        } catch (IOException e) {
            log.warn("Failed to delete file: {}", key);
        }
    }
}
