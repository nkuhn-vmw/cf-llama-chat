package com.example.cfchat.service;

import com.example.cfchat.model.UserMemory;
import com.example.cfchat.repository.UserMemoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemoryServiceTest {

    @Mock
    private UserMemoryRepository memoryRepo;

    @InjectMocks
    private MemoryService memoryService;

    @Test
    void getUserMemories_returnsUserMemories() {
        UUID userId = UUID.randomUUID();
        UserMemory memory = buildMemory(userId, "Remember this", "general");
        when(memoryRepo.findByUserIdOrderByUpdatedAtDesc(userId)).thenReturn(List.of(memory));

        List<UserMemory> result = memoryService.getUserMemories(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getContent()).isEqualTo("Remember this");
    }

    @Test
    void getUserMemories_noMemories_returnsEmptyList() {
        UUID userId = UUID.randomUUID();
        when(memoryRepo.findByUserIdOrderByUpdatedAtDesc(userId)).thenReturn(List.of());

        List<UserMemory> result = memoryService.getUserMemories(userId);

        assertThat(result).isEmpty();
    }

    @Test
    void searchMemories_returnsMatchingMemories() {
        UUID userId = UUID.randomUUID();
        UserMemory memory = buildMemory(userId, "Java programming tips", "tech");
        when(memoryRepo.searchByContent(userId, "Java")).thenReturn(List.of(memory));

        List<UserMemory> result = memoryService.searchMemories(userId, "Java");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getContent()).isEqualTo("Java programming tips");
    }

    @Test
    void buildMemoryContext_withRelevantMemories_buildsContextString() {
        UUID userId = UUID.randomUUID();
        UserMemory mem1 = buildMemory(userId, "Prefers dark mode", "preferences");
        UserMemory mem2 = buildMemory(userId, "Uses IntelliJ IDEA", null);
        when(memoryRepo.searchByContent(userId, "preferences")).thenReturn(List.of(mem1, mem2));

        String result = memoryService.buildMemoryContext(userId, "preferences");

        assertThat(result).startsWith("User's stored memories:");
        assertThat(result).contains("- Prefers dark mode [preferences]");
        assertThat(result).contains("- Uses IntelliJ IDEA");
    }

    @Test
    void buildMemoryContext_noRelevantMemories_returnsNull() {
        UUID userId = UUID.randomUUID();
        when(memoryRepo.searchByContent(userId, "nothing")).thenReturn(List.of());

        String result = memoryService.buildMemoryContext(userId, "nothing");

        assertThat(result).isNull();
    }

    @Test
    void buildMemoryContext_limitsToFiveMemories() {
        UUID userId = UUID.randomUUID();
        List<UserMemory> memories = List.of(
                buildMemory(userId, "Memory 1", null),
                buildMemory(userId, "Memory 2", null),
                buildMemory(userId, "Memory 3", null),
                buildMemory(userId, "Memory 4", null),
                buildMemory(userId, "Memory 5", null),
                buildMemory(userId, "Memory 6", null),
                buildMemory(userId, "Memory 7", null)
        );
        when(memoryRepo.searchByContent(userId, "query")).thenReturn(memories);

        String result = memoryService.buildMemoryContext(userId, "query");

        // Should contain only 5 memory entries (the limit)
        long count = result.lines().filter(line -> line.startsWith("- Memory")).count();
        assertThat(count).isEqualTo(5);
        assertThat(result).doesNotContain("Memory 6");
        assertThat(result).doesNotContain("Memory 7");
    }

    @Test
    void buildMemoryContext_memoryWithCategory_includesCategoryBrackets() {
        UUID userId = UUID.randomUUID();
        UserMemory mem = buildMemory(userId, "Some fact", "science");
        when(memoryRepo.searchByContent(userId, "fact")).thenReturn(List.of(mem));

        String result = memoryService.buildMemoryContext(userId, "fact");

        assertThat(result).contains("[science]");
    }

    @Test
    void buildMemoryContext_memoryWithoutCategory_omitsCategoryBrackets() {
        UUID userId = UUID.randomUUID();
        UserMemory mem = buildMemory(userId, "No category fact", null);
        when(memoryRepo.searchByContent(userId, "fact")).thenReturn(List.of(mem));

        String result = memoryService.buildMemoryContext(userId, "fact");

        assertThat(result).doesNotContain("[");
    }

    @Test
    void addMemory_underLimit_savesMemory() {
        UUID userId = UUID.randomUUID();
        ReflectionTestUtils.setField(memoryService, "maxMemoriesPerUser", 100);
        when(memoryRepo.countByUserId(userId)).thenReturn(5L);
        when(memoryRepo.save(any(UserMemory.class))).thenAnswer(i -> i.getArgument(0));

        UserMemory result = memoryService.addMemory(userId, "New memory", "general");

        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getContent()).isEqualTo("New memory");
        assertThat(result.getCategory()).isEqualTo("general");
        verify(memoryRepo).save(any(UserMemory.class));
    }

    @Test
    void addMemory_atLimit_throwsException() {
        UUID userId = UUID.randomUUID();
        ReflectionTestUtils.setField(memoryService, "maxMemoriesPerUser", 100);
        when(memoryRepo.countByUserId(userId)).thenReturn(100L);

        assertThatThrownBy(() -> memoryService.addMemory(userId, "Over limit", "general"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Memory limit reached");
    }

    @Test
    void updateMemory_existing_updatesContentAndCategory() {
        UUID memoryId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UserMemory existing = buildMemory(userId, "Old content", "old-cat");
        existing.setId(memoryId);
        when(memoryRepo.findById(memoryId)).thenReturn(Optional.of(existing));
        when(memoryRepo.save(any(UserMemory.class))).thenAnswer(i -> i.getArgument(0));

        UserMemory result = memoryService.updateMemory(memoryId, userId, "New content", "new-cat");

        assertThat(result.getContent()).isEqualTo("New content");
        assertThat(result.getCategory()).isEqualTo("new-cat");
    }

    @Test
    void updateMemory_nullContent_keepsExistingContent() {
        UUID memoryId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UserMemory existing = buildMemory(userId, "Original content", "cat");
        existing.setId(memoryId);
        when(memoryRepo.findById(memoryId)).thenReturn(Optional.of(existing));
        when(memoryRepo.save(any(UserMemory.class))).thenAnswer(i -> i.getArgument(0));

        UserMemory result = memoryService.updateMemory(memoryId, userId, null, "new-cat");

        assertThat(result.getContent()).isEqualTo("Original content");
        assertThat(result.getCategory()).isEqualTo("new-cat");
    }

    @Test
    void updateMemory_nullCategory_keepsExistingCategory() {
        UUID memoryId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UserMemory existing = buildMemory(userId, "Content", "original-cat");
        existing.setId(memoryId);
        when(memoryRepo.findById(memoryId)).thenReturn(Optional.of(existing));
        when(memoryRepo.save(any(UserMemory.class))).thenAnswer(i -> i.getArgument(0));

        UserMemory result = memoryService.updateMemory(memoryId, userId, "Updated", null);

        assertThat(result.getContent()).isEqualTo("Updated");
        assertThat(result.getCategory()).isEqualTo("original-cat");
    }

    @Test
    void updateMemory_notFound_throwsException() {
        UUID memoryId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(memoryRepo.findById(memoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memoryService.updateMemory(memoryId, userId, "content", "cat"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Memory not found");
    }

    @Test
    void updateMemory_wrongUser_throwsSecurityException() {
        UUID memoryId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID attackerId = UUID.randomUUID();
        UserMemory existing = buildMemory(ownerId, "Content", "cat");
        existing.setId(memoryId);
        when(memoryRepo.findById(memoryId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> memoryService.updateMemory(memoryId, attackerId, "hacked", "cat"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void deleteMemory_existing_deletesById() {
        UUID memoryId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UserMemory existing = buildMemory(userId, "To delete", "cat");
        existing.setId(memoryId);
        when(memoryRepo.findById(memoryId)).thenReturn(Optional.of(existing));

        memoryService.deleteMemory(memoryId, userId);

        verify(memoryRepo).deleteById(memoryId);
    }

    @Test
    void deleteMemory_notFound_throwsException() {
        UUID memoryId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(memoryRepo.findById(memoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memoryService.deleteMemory(memoryId, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Memory not found");
    }

    @Test
    void deleteMemory_wrongUser_throwsSecurityException() {
        UUID memoryId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID attackerId = UUID.randomUUID();
        UserMemory existing = buildMemory(ownerId, "Content", "cat");
        existing.setId(memoryId);
        when(memoryRepo.findById(memoryId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> memoryService.deleteMemory(memoryId, attackerId))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Access denied");
    }

    private UserMemory buildMemory(UUID userId, String content, String category) {
        return UserMemory.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .content(content)
                .category(category)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
