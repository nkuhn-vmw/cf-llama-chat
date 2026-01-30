package com.example.cfchat.repository;

import com.example.cfchat.model.Tool;
import com.example.cfchat.model.ToolType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ToolRepository extends JpaRepository<Tool, UUID> {

    List<Tool> findByEnabled(boolean enabled);

    List<Tool> findByMcpServerId(UUID mcpServerId);

    List<Tool> findByMcpServerIdAndEnabled(UUID mcpServerId, boolean enabled);

    Optional<Tool> findByName(String name);

    boolean existsByName(String name);

    List<Tool> findByType(ToolType type);

    @Query("SELECT t FROM Tool t WHERE t.enabled = true AND t.id IN :ids")
    List<Tool> findEnabledByIds(@Param("ids") List<UUID> ids);

    void deleteByMcpServerId(UUID mcpServerId);
}
