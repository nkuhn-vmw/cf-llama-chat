package com.example.cfchat.repository;

import com.example.cfchat.model.UserDocument;
import com.example.cfchat.model.UserDocument.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for user document metadata operations.
 */
@Repository
public interface UserDocumentRepository extends JpaRepository<UserDocument, UUID> {

    /**
     * Find all documents for a specific user.
     */
    List<UserDocument> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Find all documents for a user with a specific status.
     */
    List<UserDocument> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, DocumentStatus status);

    /**
     * Find a document by ID and user ID (for ownership verification).
     */
    Optional<UserDocument> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Check if a document exists for a user with the same filename.
     */
    boolean existsByUserIdAndOriginalFilename(UUID userId, String originalFilename);

    /**
     * Count documents by user.
     */
    long countByUserId(UUID userId);

    /**
     * Count documents by user and status.
     */
    long countByUserIdAndStatus(UUID userId, DocumentStatus status);

    /**
     * Get total storage used by a user (sum of file sizes).
     */
    @Query("SELECT COALESCE(SUM(d.fileSize), 0) FROM UserDocument d WHERE d.user.id = :userId")
    long getTotalStorageByUserId(@Param("userId") UUID userId);

    /**
     * Find documents pending processing.
     */
    List<UserDocument> findByStatusOrderByCreatedAtAsc(DocumentStatus status);

    /**
     * Delete all documents for a user.
     */
    void deleteByUserId(UUID userId);
}
