package com.example.cfchat.service;

import com.example.cfchat.model.ModelAccessRule;
import com.example.cfchat.model.NotificationBanner;
import com.example.cfchat.model.PromptPreset;
import com.example.cfchat.model.SystemSetting;
import com.example.cfchat.repository.ModelAccessRuleRepository;
import com.example.cfchat.repository.NotificationBannerRepository;
import com.example.cfchat.repository.PromptPresetRepository;
import com.example.cfchat.repository.SystemSettingRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for exporting and importing system configuration.
 * Gathers data from all configuration-related repositories and serializes/deserializes as JSON.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigExportService {

    private final SystemSettingRepository systemSettingRepository;
    private final PromptPresetRepository promptPresetRepository;
    private final ModelAccessRuleRepository modelAccessRuleRepository;
    private final NotificationBannerRepository notificationBannerRepository;

    private static final ObjectMapper objectMapper = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * Export all system configuration as a JSON byte array.
     */
    public byte[] exportConfig() {
        try {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("exportedAt", LocalDateTime.now().toString());
            config.put("version", "1.0");

            // System settings
            List<SystemSetting> settings = systemSettingRepository.findAll();
            config.put("systemSettings", settings);

            // Shared prompt presets (only shared ones for portability)
            List<PromptPreset> presets = promptPresetRepository.findBySharedTrueOrderByCommandAsc();
            config.put("promptPresets", presets);

            // Model access rules
            List<ModelAccessRule> accessRules = modelAccessRuleRepository.findAll();
            config.put("modelAccessRules", accessRules);

            // Notification banners
            List<NotificationBanner> banners = notificationBannerRepository.findAll();
            config.put("notificationBanners", banners);

            return objectMapper.writeValueAsBytes(config);
        } catch (Exception e) {
            log.error("Failed to export configuration: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to export configuration", e);
        }
    }

    /**
     * Import configuration from a JSON byte array.
     * Returns a summary of what was imported.
     */
    @Transactional
    public Map<String, Object> importConfig(byte[] data) {
        try {
            Map<String, Object> config = objectMapper.readValue(data, new TypeReference<>() {});
            Map<String, Object> result = new HashMap<>();
            int totalImported = 0;

            // Import system settings
            if (config.containsKey("systemSettings")) {
                List<SystemSetting> settings = objectMapper.convertValue(
                        config.get("systemSettings"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, SystemSetting.class));
                int count = 0;
                for (SystemSetting setting : settings) {
                    systemSettingRepository.findByKey(setting.getKey())
                            .ifPresentOrElse(
                                    existing -> {
                                        existing.setValue(setting.getValue());
                                        systemSettingRepository.save(existing);
                                    },
                                    () -> {
                                        setting.setId(null);
                                        systemSettingRepository.save(setting);
                                    });
                    count++;
                }
                result.put("systemSettings", count);
                totalImported += count;
                log.info("Imported {} system settings", count);
            }

            // Import prompt presets
            if (config.containsKey("promptPresets")) {
                List<PromptPreset> presets = objectMapper.convertValue(
                        config.get("promptPresets"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, PromptPreset.class));
                int count = 0;
                for (PromptPreset preset : presets) {
                    preset.setId(null); // Generate new IDs
                    promptPresetRepository.save(preset);
                    count++;
                }
                result.put("promptPresets", count);
                totalImported += count;
                log.info("Imported {} prompt presets", count);
            }

            // Import model access rules
            if (config.containsKey("modelAccessRules")) {
                List<ModelAccessRule> rules = objectMapper.convertValue(
                        config.get("modelAccessRules"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, ModelAccessRule.class));
                int count = 0;
                for (ModelAccessRule rule : rules) {
                    rule.setId(null);
                    modelAccessRuleRepository.save(rule);
                    count++;
                }
                result.put("modelAccessRules", count);
                totalImported += count;
                log.info("Imported {} model access rules", count);
            }

            // Import notification banners
            if (config.containsKey("notificationBanners")) {
                List<NotificationBanner> banners = objectMapper.convertValue(
                        config.get("notificationBanners"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, NotificationBanner.class));
                int count = 0;
                for (NotificationBanner banner : banners) {
                    banner.setId(null);
                    notificationBannerRepository.save(banner);
                    count++;
                }
                result.put("notificationBanners", count);
                totalImported += count;
                log.info("Imported {} notification banners", count);
            }

            result.put("totalImported", totalImported);
            result.put("success", true);
            return result;

        } catch (Exception e) {
            log.error("Failed to import configuration: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to import configuration: " + e.getMessage(), e);
        }
    }
}
