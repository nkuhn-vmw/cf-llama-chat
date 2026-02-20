package com.example.cfchat.service;

public interface DocumentExtractor {
    String extract(byte[] fileContent, String filename);
}
