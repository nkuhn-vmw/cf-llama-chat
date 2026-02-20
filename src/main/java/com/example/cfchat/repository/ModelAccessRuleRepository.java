package com.example.cfchat.repository;

import com.example.cfchat.model.ModelAccessRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ModelAccessRuleRepository extends JpaRepository<ModelAccessRule, Long> {
    List<ModelAccessRule> findByRoleNameAndAllowedTrue(String roleName);

    List<ModelAccessRule> findByModelId(String modelId);

    @Query("SELECT r.modelId FROM ModelAccessRule r WHERE r.roleName = :role AND r.allowed = true")
    List<String> findAllowedModelIds(@Param("role") String roleName);

    void deleteByModelIdAndRoleName(String modelId, String roleName);
}
