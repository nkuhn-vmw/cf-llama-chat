package com.example.cfchat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.Map;

@Component
@Slf4j
@ConditionalOnProperty(name = "rag.extractor", havingValue = "docling")
public class DoclingExtractor implements DocumentExtractor {
    @Value("${rag.docling.url:http://localhost:5000}") private String doclingUrl;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String extract(byte[] fileContent, String filename) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, String> body = Map.of(
                "content", Base64.getEncoder().encodeToString(fileContent),
                "filename", filename
            );
            ResponseEntity<Map> response = restTemplate.exchange(
                doclingUrl + "/convert",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class
            );
            if (response.getBody() != null && response.getBody().containsKey("text")) {
                return response.getBody().get("text").toString();
            }
            return "";
        } catch (Exception e) {
            log.error("Docling extraction failed for {}: {}", filename, e.getMessage());
            return "";
        }
    }
}
