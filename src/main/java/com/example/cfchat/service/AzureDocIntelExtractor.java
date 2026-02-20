package com.example.cfchat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@Slf4j
@ConditionalOnProperty(name = "rag.extractor", havingValue = "azure-di")
public class AzureDocIntelExtractor implements DocumentExtractor {
    @Value("${rag.azure-di.endpoint:}") private String endpoint;
    @Value("${rag.azure-di.key:}") private String apiKey;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String extract(byte[] fileContent, String filename) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Ocp-Apim-Subscription-Key", apiKey);
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            ResponseEntity<Map> response = restTemplate.exchange(
                endpoint + "/formrecognizer/documentModels/prebuilt-read:analyze?api-version=2023-07-31",
                HttpMethod.POST,
                new HttpEntity<>(fileContent, headers),
                Map.class
            );
            String resultUrl = response.getHeaders().getFirst("Operation-Location");
            if (resultUrl != null) {
                Thread.sleep(2000);
                HttpHeaders pollHeaders = new HttpHeaders();
                pollHeaders.set("Ocp-Apim-Subscription-Key", apiKey);
                ResponseEntity<Map> result = restTemplate.exchange(resultUrl, HttpMethod.GET, new HttpEntity<>(pollHeaders), Map.class);
                if (result.getBody() != null) {
                    Map analyzeResult = (Map) result.getBody().get("analyzeResult");
                    if (analyzeResult != null) return analyzeResult.getOrDefault("content", "").toString();
                }
            }
            return "";
        } catch (Exception e) {
            log.error("Azure DI extraction failed for {}: {}", filename, e.getMessage());
            return "";
        }
    }
}
