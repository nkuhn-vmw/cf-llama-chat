package com.example.cfchat.repository;

import com.example.cfchat.model.UserGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserGroupRepository extends JpaRepository<UserGroup, Long> {

    Optional<UserGroup> findByName(String name);

    boolean existsByName(String name);

    @Query("SELECT g FROM UserGroup g JOIN g.members m WHERE m.id = :userId")
    List<UserGroup> findByMembersId(@Param("userId") UUID userId);
}
