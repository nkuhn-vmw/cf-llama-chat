package com.example.cfchat.service;

import com.example.cfchat.model.Webhook;
import com.example.cfchat.repository.WebhookRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private final WebhookRepository webhookRepo;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Async
    public void fire(String eventType, Map<String, Object> payload) {
        webhookRepo.findByEventTypeAndEnabledTrue(eventType).forEach(hook -> {
            try {
                String body = formatForPlatform(hook.getPlatform(), eventType, payload);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                if (hook.getSecret() != null && !hook.getSecret().isBlank()) {
                    headers.set("X-Webhook-Signature", hmacSha256(hook.getSecret(), body));
                }
                restTemplate.postForEntity(hook.getUrl(), new HttpEntity<>(body, headers), String.class);
                log.debug("Webhook fired successfully: {} -> {}", eventType, hook.getName());
            } catch (Exception e) {
                log.warn("Webhook delivery failed for {}: {}", hook.getName(), e.getMessage());
            }
        });
    }

    private String formatForPlatform(String platform, String eventType, Map<String, Object> payload) {
        try {
            if ("slack".equalsIgnoreCase(platform)) {
                return objectMapper.writeValueAsString(Map.of("text", formatMessage(eventType, payload)));
            } else if ("discord".equalsIgnoreCase(platform)) {
                return objectMapper.writeValueAsString(Map.of("content", formatMessage(eventType, payload)));
            } else if ("teams".equalsIgnoreCase(platform)) {
                return objectMapper.writeValueAsString(Map.of("text", formatMessage(eventType, payload)));
            }
            return objectMapper.writeValueAsString(Map.of("event", eventType, "data", payload));
        } catch (Exception e) {
            return "{\"event\":\"" + eventType + "\"}";
        }
    }

    private String formatMessage(String eventType, Map<String, Object> payload) {
        return "[CF Llama Chat] " + eventType + ": " + payload.toString();
    }

    private String hmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "";
        }
    }

    @Transactional(readOnly = true)
    public List<Webhook> getAllWebhooks() { return webhookRepo.findAll(); }

    @Transactional
    public Webhook create(Webhook webhook) { return webhookRepo.save(webhook); }

    @Transactional
    public void delete(Long id) { webhookRepo.deleteById(id); }
}
