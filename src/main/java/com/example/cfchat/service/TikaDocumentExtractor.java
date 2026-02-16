package com.example.cfchat.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

@Component
@Slf4j
@ConditionalOnProperty(name = "rag.extractor", havingValue = "tika", matchIfMissing = true)
public class TikaDocumentExtractor implements DocumentExtractor {
    private final Tika tika = new Tika();

    @Override
    public String extract(byte[] fileContent, String filename) {
        try {
            return tika.parseToString(new ByteArrayInputStream(fileContent));
        } catch (Exception e) {
            log.error("Tika extraction failed for {}: {}", filename, e.getMessage());
            return "";
        }
    }
}
