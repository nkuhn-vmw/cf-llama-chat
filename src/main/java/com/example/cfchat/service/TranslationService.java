package com.example.cfchat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@ConditionalOnProperty(name = "translation.enabled", havingValue = "true")
@Slf4j
public class TranslationService {

    @Value("${translation.provider:libretranslate}")
    private String provider;

    @Value("${translation.libretranslate.url:http://localhost:5000}")
    private String libreTranslateUrl;

    private final RestTemplate restTemplate;

    public TranslationService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Translate text from source language to target language.
     *
     * @param text       the text to translate
     * @param sourceLang source language code (e.g., "en", "auto")
     * @param targetLang target language code (e.g., "es", "fr")
     * @return translated text, or original text if translation fails
     */
    public String translate(String text, String sourceLang, String targetLang) {
        if (text == null || text.isBlank()) {
            return text;
        }

        try {
            if ("libretranslate".equalsIgnoreCase(provider)) {
                return translateWithLibreTranslate(text, sourceLang, targetLang);
            } else {
                log.warn("Unsupported translation provider: {}", provider);
                return text;
            }
        } catch (Exception e) {
            log.error("Translation failed for provider {}: {}", provider, e.getMessage());
            return text;
        }
    }

    private String translateWithLibreTranslate(String text, String sourceLang, String targetLang) {
        String url = libreTranslateUrl + "/translate";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> requestBody = Map.of(
                "q", text,
                "source", sourceLang != null ? sourceLang : "auto",
                "target", targetLang
        );

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);

        if (response != null && response.containsKey("translatedText")) {
            String translatedText = (String) response.get("translatedText");
            log.debug("Translated text from {} to {}: {} -> {}", sourceLang, targetLang,
                    text.substring(0, Math.min(50, text.length())),
                    translatedText.substring(0, Math.min(50, translatedText.length())));
            return translatedText;
        }

        log.warn("LibreTranslate returned unexpected response: {}", response);
        return text;
    }
}
