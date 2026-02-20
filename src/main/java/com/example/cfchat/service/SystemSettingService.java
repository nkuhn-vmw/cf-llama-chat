package com.example.cfchat.service;

import com.example.cfchat.model.SystemSetting;
import com.example.cfchat.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemSettingService {

    private final SystemSettingRepository systemSettingRepository;

    @Autowired(required = false)
    private EventService eventService;

    @Transactional(readOnly = true)
    public String getSetting(String key, String defaultValue) {
        return systemSettingRepository.findByKey(key)
                .map(SystemSetting::getValue)
                .orElse(defaultValue);
    }

    @Transactional
    public void setSetting(String key, String value) {
        Optional<SystemSetting> existing = systemSettingRepository.findByKey(key);
        if (existing.isPresent()) {
            SystemSetting setting = existing.get();
            setting.setValue(value);
            setting.setUpdatedAt(LocalDateTime.now());
            systemSettingRepository.save(setting);
        } else {
            SystemSetting setting = SystemSetting.builder()
                    .key(key)
                    .value(value)
                    .build();
            systemSettingRepository.save(setting);
        }
        log.info("System setting updated: {} = {}", key, value);
        if (eventService != null) {
            eventService.broadcast("cache.settings", key);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, String> getAllSettings() {
        Map<String, String> result = new HashMap<>();
        systemSettingRepository.findAll().forEach(s -> result.put(s.getKey(), s.getValue()));
        return result;
    }

    @Transactional(readOnly = true)
    public boolean getBooleanSetting(String key, boolean defaultValue) {
        String value = getSetting(key, null);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }
}
