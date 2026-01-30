package com.example.cfchat.repository;

import com.example.cfchat.model.McpServer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface McpServerRepository extends JpaRepository<McpServer, UUID> {

    List<McpServer> findByEnabled(boolean enabled);

    Optional<McpServer> findByName(String name);

    boolean existsByName(String name);
}
