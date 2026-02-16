package com.example.cfchat.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "model_knowledge", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"model_id", "document_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModelKnowledge {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "model_id", nullable = false)
    private String modelId;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
