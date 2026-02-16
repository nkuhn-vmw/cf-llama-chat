package com.example.cfchat.repository;

import com.example.cfchat.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
    List<Permission> findByCategory(String category);
    List<Permission> findByCodeIn(Collection<String> codes);
}
