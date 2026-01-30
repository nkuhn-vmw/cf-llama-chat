package com.example.cfchat.repository;

import com.example.cfchat.model.AccessType;
import com.example.cfchat.model.UserAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserAccessRepository extends JpaRepository<UserAccess, UUID> {

    List<UserAccess> findByUserId(UUID userId);

    List<UserAccess> findByUserIdAndAccessType(UUID userId, AccessType accessType);

    Optional<UserAccess> findByUserIdAndAccessTypeAndResourceId(UUID userId, AccessType accessType, UUID resourceId);

    boolean existsByUserIdAndAccessTypeAndResourceId(UUID userId, AccessType accessType, UUID resourceId);

    @Query("SELECT ua FROM UserAccess ua WHERE ua.userId = :userId AND ua.accessType = :accessType AND ua.allowed = true")
    List<UserAccess> findAllowedByUserIdAndAccessType(@Param("userId") UUID userId, @Param("accessType") AccessType accessType);

    @Query("SELECT ua.resourceId FROM UserAccess ua WHERE ua.userId = :userId AND ua.accessType = :accessType AND ua.allowed = true")
    List<UUID> findAllowedResourceIds(@Param("userId") UUID userId, @Param("accessType") AccessType accessType);

    void deleteByUserIdAndAccessTypeAndResourceId(UUID userId, AccessType accessType, UUID resourceId);

    void deleteByUserId(UUID userId);

    void deleteByResourceIdAndAccessType(UUID resourceId, AccessType accessType);
}
