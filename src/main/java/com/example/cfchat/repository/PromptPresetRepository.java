package com.example.cfchat.repository;

import com.example.cfchat.model.PromptPreset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PromptPresetRepository extends JpaRepository<PromptPreset, UUID> {

    List<PromptPreset> findByOwnerIdOrderByCommandAsc(UUID ownerId);

    @Query("SELECT p FROM PromptPreset p WHERE (p.ownerId = :uid OR p.shared = true) ORDER BY p.command ASC")
    List<PromptPreset> findAccessibleByUserId(@Param("uid") UUID userId);

    @Query("SELECT p FROM PromptPreset p WHERE (p.ownerId = :uid OR p.shared = true) AND (LOWER(p.command) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(p.title) LIKE LOWER(CONCAT('%',:q,'%')))")
    List<PromptPreset> searchAccessible(@Param("uid") UUID userId, @Param("q") String query);

    List<PromptPreset> findBySharedTrueOrderByCommandAsc();
}
