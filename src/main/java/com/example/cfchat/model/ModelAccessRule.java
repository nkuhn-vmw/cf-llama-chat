package com.example.cfchat.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "model_access_rules", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"model_id", "role_name"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModelAccessRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_id", nullable = false, length = 255)
    private String modelId;

    @Column(name = "role_name", nullable = false, length = 50)
    private String roleName;

    @Column(name = "allowed")
    @Builder.Default
    private boolean allowed = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
