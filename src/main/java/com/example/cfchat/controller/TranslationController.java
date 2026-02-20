package com.example.cfchat.controller;

import com.example.cfchat.service.TranslationService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/translate")
@Slf4j
public class TranslationController {

    private final TranslationService translationService;

    public TranslationController(@Autowired(required = false) TranslationService translationService) {
        this.translationService = translationService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> translate(@RequestBody TranslateRequest request) {
        if (translationService == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Translation service is not enabled. Set translation.enabled=true to enable it."
            ));
        }

        if (request.getText() == null || request.getText().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Text to translate is required"
            ));
        }

        if (request.getTarget() == null || request.getTarget().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Target language is required"
            ));
        }

        try {
            String translatedText = translationService.translate(
                    request.getText(),
                    request.getSource(),
                    request.getTarget()
            );

            return ResponseEntity.ok(Map.of("translatedText", translatedText));
        } catch (Exception e) {
            log.error("Translation request failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Translation failed: " + e.getMessage()
            ));
        }
    }

    @Data
    public static class TranslateRequest {
        private String text;
        private String source;
        private String target;
    }
}
