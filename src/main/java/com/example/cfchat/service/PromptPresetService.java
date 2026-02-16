package com.example.cfchat.service;

import com.example.cfchat.model.PromptPreset;
import com.example.cfchat.repository.PromptPresetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PromptPresetService {

    private final PromptPresetRepository presetRepository;

    @Transactional(readOnly = true)
    public List<PromptPreset> getAccessiblePresets(UUID userId) {
        return presetRepository.findAccessibleByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<PromptPreset> searchPresets(UUID userId, String query) {
        return presetRepository.searchAccessible(userId, query);
    }

    @Transactional(readOnly = true)
    public List<PromptPreset> getUserPresets(UUID userId) {
        return presetRepository.findByOwnerIdOrderByCommandAsc(userId);
    }

    @Transactional(readOnly = true)
    public List<PromptPreset> getSharedPresets() {
        return presetRepository.findBySharedTrueOrderByCommandAsc();
    }

    @Transactional(readOnly = true)
    public Optional<PromptPreset> getById(UUID id) {
        return presetRepository.findById(id);
    }

    @Transactional
    public PromptPreset create(UUID ownerId, String command, String title, String content, String description, boolean shared) {
        PromptPreset preset = PromptPreset.builder()
                .ownerId(ownerId)
                .command(command.startsWith("/") ? command : "/" + command)
                .title(title)
                .content(content)
                .description(description)
                .shared(shared)
                .build();
        return presetRepository.save(preset);
    }

    @Transactional
    public PromptPreset update(UUID id, UUID userId, String command, String title, String content, String description, Boolean shared) {
        PromptPreset preset = presetRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Preset not found: " + id));

        if (!preset.getOwnerId().equals(userId)) {
            throw new SecurityException("Not authorized to modify this preset");
        }

        if (command != null) preset.setCommand(command.startsWith("/") ? command : "/" + command);
        if (title != null) preset.setTitle(title);
        if (content != null) preset.setContent(content);
        if (description != null) preset.setDescription(description);
        if (shared != null) preset.setShared(shared);

        return presetRepository.save(preset);
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        PromptPreset preset = presetRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Preset not found: " + id));

        if (!preset.getOwnerId().equals(userId)) {
            throw new SecurityException("Not authorized to delete this preset");
        }

        presetRepository.deleteById(id);
    }
}
