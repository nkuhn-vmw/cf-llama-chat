package com.example.cfchat.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mcp_servers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class McpServer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "transport_type", nullable = false)
    private McpTransportType transportType;

    @Column
    private String url;

    @Column
    private String command;

    @Column(columnDefinition = "TEXT")
    private String args;

    @Column(name = "env_vars", columnDefinition = "TEXT")
    private String envVars;

    @Column(columnDefinition = "TEXT")
    private String headers;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "requires_auth")
    @Builder.Default
    private boolean requiresAuth = false;

    @Column(name = "oauth_config", columnDefinition = "TEXT")
    private String oauthConfig;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
