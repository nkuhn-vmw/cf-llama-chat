package com.example.cfchat.repository;

import com.example.cfchat.model.DocumentStorageConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for DocumentStorageConfig entity.
 * Note: This is a singleton configuration - only one record should exist.
 */
@Repository
public interface DocumentStorageConfigRepository extends JpaRepository<DocumentStorageConfig, UUID> {

    /**
     * Get the current storage configuration.
     * Returns the first (and should be only) configuration record.
     */
    @Query("SELECT c FROM DocumentStorageConfig c ORDER BY c.createdAt ASC")
    Optional<DocumentStorageConfig> findConfiguration();

    /**
     * Check if S3 storage is enabled.
     */
    @Query("SELECT c.enabled FROM DocumentStorageConfig c ORDER BY c.createdAt ASC")
    Optional<Boolean> isEnabled();
}
