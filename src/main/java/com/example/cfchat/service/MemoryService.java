package com.example.cfchat.service;

import com.example.cfchat.model.UserMemory;
import com.example.cfchat.repository.UserMemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemoryService {

    private final UserMemoryRepository memoryRepo;

    @Value("${app.memory.max-per-user:100}")
    private int maxMemoriesPerUser;

    @Transactional(readOnly = true)
    public List<UserMemory> getUserMemories(UUID userId) {
        return memoryRepo.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<UserMemory> searchMemories(UUID userId, String query) {
        return memoryRepo.searchByContent(userId, query);
    }

    @Transactional(readOnly = true)
    public String buildMemoryContext(UUID userId, String query) {
        List<UserMemory> relevant = memoryRepo.searchByContent(userId, query);
        if (relevant.isEmpty()) return null;

        StringBuilder sb = new StringBuilder("User's stored memories:\n");
        relevant.stream().limit(5).forEach(m -> {
            sb.append("- ").append(m.getContent());
            if (m.getCategory() != null) sb.append(" [").append(m.getCategory()).append("]");
            sb.append("\n");
        });
        return sb.toString();
    }

    @Transactional
    public UserMemory addMemory(UUID userId, String content, String category) {
        if (memoryRepo.countByUserId(userId) >= maxMemoriesPerUser) {
            throw new IllegalStateException("Memory limit reached (" + maxMemoriesPerUser + ")");
        }
        UserMemory memory = UserMemory.builder()
                .userId(userId).content(content).category(category).build();
        return memoryRepo.save(memory);
    }

    @Transactional
    public UserMemory updateMemory(UUID id, UUID userId, String content, String category) {
        UserMemory memory = memoryRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        if (!memory.getUserId().equals(userId)) throw new SecurityException("Access denied");
        if (content != null) memory.setContent(content);
        if (category != null) memory.setCategory(category);
        return memoryRepo.save(memory);
    }

    @Transactional
    public void deleteMemory(UUID id, UUID userId) {
        UserMemory memory = memoryRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        if (!memory.getUserId().equals(userId)) throw new SecurityException("Access denied");
        memoryRepo.deleteById(id);
    }
}
