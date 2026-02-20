package com.example.cfchat.repository;

import com.example.cfchat.model.ModelKnowledge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ModelKnowledgeRepository extends JpaRepository<ModelKnowledge, UUID> {
    List<ModelKnowledge> findByModelId(String modelId);
    void deleteByModelIdAndDocumentId(String modelId, String documentId);
    boolean existsByModelIdAndDocumentId(String modelId, String documentId);
}
