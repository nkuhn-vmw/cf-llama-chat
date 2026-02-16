package com.example.cfchat.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "prompt_presets", indexes = {
        @Index(name = "idx_preset_owner", columnList = "owner_id"),
        @Index(name = "idx_preset_command", columnList = "command")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromptPreset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String command;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(length = 500)
    private String description;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "shared")
    @Builder.Default
    private boolean shared = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
