package com.example.cfchat.repository;

import com.example.cfchat.model.UserMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserMemoryRepository extends JpaRepository<UserMemory, UUID> {
    List<UserMemory> findByUserIdOrderByUpdatedAtDesc(UUID userId);
    List<UserMemory> findByUserIdAndCategoryOrderByUpdatedAtDesc(UUID userId, String category);

    @Query("SELECT m FROM UserMemory m WHERE m.userId = :uid AND LOWER(m.content) LIKE LOWER(CONCAT('%',:q,'%'))")
    List<UserMemory> searchByContent(@Param("uid") UUID userId, @Param("q") String query);

    long countByUserId(UUID userId);
}
