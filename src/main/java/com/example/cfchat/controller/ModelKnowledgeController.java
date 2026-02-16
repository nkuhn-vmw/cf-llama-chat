package com.example.cfchat.controller;

import com.example.cfchat.model.ModelKnowledge;
import com.example.cfchat.repository.ModelKnowledgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/model-knowledge")
@RequiredArgsConstructor
public class ModelKnowledgeController {

    private final ModelKnowledgeRepository repository;

    public record AttachRequest(String modelId, String documentId) {}

    @GetMapping("/{modelId}")
    public List<ModelKnowledge> getKnowledge(@PathVariable String modelId) {
        return repository.findByModelId(modelId);
    }

    @PostMapping
    public ResponseEntity<ModelKnowledge> attach(@RequestBody AttachRequest request,
                                                  @AuthenticationPrincipal UserDetails user) {
        if (repository.existsByModelIdAndDocumentId(request.modelId(), request.documentId())) {
            return ResponseEntity.ok().build();
        }
        ModelKnowledge mk = ModelKnowledge.builder()
                .modelId(request.modelId())
                .documentId(request.documentId())
                .build();
        return ResponseEntity.ok(repository.save(mk));
    }

    @DeleteMapping("/{modelId}/{documentId}")
    public ResponseEntity<Void> detach(@PathVariable String modelId, @PathVariable String documentId) {
        repository.deleteByModelIdAndDocumentId(modelId, documentId);
        return ResponseEntity.ok().build();
    }
}
