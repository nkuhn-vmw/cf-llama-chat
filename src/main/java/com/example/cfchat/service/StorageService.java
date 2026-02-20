package com.example.cfchat.service;

public interface StorageService {
    String store(String key, byte[] data, String contentType);
    byte[] retrieve(String key);
    void delete(String key);
}
