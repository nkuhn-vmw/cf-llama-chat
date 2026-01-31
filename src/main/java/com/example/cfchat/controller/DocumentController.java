package com.example.cfchat.controller;

import com.example.cfchat.auth.UserService;
import com.example.cfchat.dto.DocumentUploadResponse;
import com.example.cfchat.dto.UserDocumentDto;
import com.example.cfchat.model.User;
import com.example.cfchat.service.DocumentEmbeddingService;
import com.example.cfchat.model.UserDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * REST controller for user document management and embedding operations.
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentController {

    private final DocumentEmbeddingService documentService;
    private final UserService userService;

    /**
     * Check if document embedding feature is available.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        boolean available = documentService.isAvailable();
        return ResponseEntity.ok(Map.of(
                "available", available,
                "message", available ?
                        "Document embedding service is available" :
                        "Document embedding service is not configured"
        ));
    }

    /**
     * Upload a document for embedding.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentUploadResponse> uploadDocument(
            @RequestParam("file") MultipartFile file) {

        User user = userService.getCurrentUser()
                .orElseThrow(() -> new IllegalStateException("User not authenticated"));

        if (!documentService.isAvailable()) {
            return ResponseEntity.badRequest().body(
                    DocumentUploadResponse.builder()
                            .status("ERROR")
                            .message("Document embedding service is not available")
                            .build()
            );
        }

        try {
            log.info("User {} uploading document: {}", user.getId(), file.getOriginalFilename());
            DocumentUploadResponse response = documentService.uploadDocument(user.getId(), file);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid document upload from user {}: {}", user.getId(), e.getMessage());
            return ResponseEntity.badRequest().body(
                    DocumentUploadResponse.builder()
                            .filename(file.getOriginalFilename())
                            .status("ERROR")
                            .message(e.getMessage())
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to upload document for user {}: {}", user.getId(), e.getMessage(), e);
            return ResponseEntity.internalServerError().body(
                    DocumentUploadResponse.builder()
                            .filename(file.getOriginalFilename())
                            .status("ERROR")
                            .message("Failed to process document: " + e.getMessage())
                            .build()
            );
        }
    }

    /**
     * Get all documents for the current user.
     */
    @GetMapping
    public ResponseEntity<List<UserDocumentDto>> getUserDocuments() {
        User user = userService.getCurrentUser()
                .orElseThrow(() -> new IllegalStateException("User not authenticated"));

        List<UserDocumentDto> documents = documentService.getUserDocuments(user.getId());
        return ResponseEntity.ok(documents);
    }

    /**
     * Get a specific document for the current user.
     */
    @GetMapping("/{documentId}")
    public ResponseEntity<UserDocumentDto> getDocument(@PathVariable UUID documentId) {
        User user = userService.getCurrentUser()
                .orElseThrow(() -> new IllegalStateException("User not authenticated"));

        return documentService.getUserDocument(user.getId(), documentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Download the original document file (requires S3 storage to be enabled).
     */
    @GetMapping("/{documentId}/download")
    public ResponseEntity<?> downloadDocument(@PathVariable UUID documentId) {
        User user = userService.getCurrentUser()
                .orElseThrow(() -> new IllegalStateException("User not authenticated"));

        try {
            // Get document metadata
            Optional<UserDocument> documentOpt = documentService.getUserDocumentEntity(user.getId(), documentId);
            if (documentOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            UserDocument document = documentOpt.get();

            // Check if document has stored file
            if (document.getStoragePath() == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Document original file is not available",
                        "message", "This document was uploaded before S3 storage was enabled"
                ));
            }

            // Get document content
            Optional<InputStream> contentOpt = documentService.getDocumentContent(user.getId(), documentId);
            if (contentOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Unable to retrieve document",
                        "message", "S3 storage may not be properly configured"
                ));
            }

            // Build response with proper headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDispositionFormData("attachment", document.getOriginalFilename());
            if (document.getContentType() != null) {
                headers.setContentType(MediaType.parseMediaType(document.getContentType()));
            }
            if (document.getFileSize() != null) {
                headers.setContentLength(document.getFileSize());
            }

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(new InputStreamResource(contentOpt.get()));

        } catch (Exception e) {
            log.error("Failed to download document {}: {}", documentId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to download document",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Delete a document and its embeddings.
     */
    @DeleteMapping("/{documentId}")
    public ResponseEntity<Map<String, Object>> deleteDocument(@PathVariable UUID documentId) {
        User user = userService.getCurrentUser()
                .orElseThrow(() -> new IllegalStateException("User not authenticated"));

        boolean deleted = documentService.deleteDocument(user.getId(), documentId);

        if (deleted) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Document deleted successfully"
            ));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Delete all documents for the current user.
     */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> deleteAllDocuments() {
        User user = userService.getCurrentUser()
                .orElseThrow(() -> new IllegalStateException("User not authenticated"));

        documentService.deleteAllUserDocuments(user.getId());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "All documents deleted successfully"
        ));
    }

    /**
     * Get document statistics for the current user.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getDocumentStats() {
        User user = userService.getCurrentUser()
                .orElseThrow(() -> new IllegalStateException("User not authenticated"));

        Map<String, Object> stats = documentService.getUserDocumentStats(user.getId());
        return ResponseEntity.ok(stats);
    }

    /**
     * Search user's documents with a query.
     */
    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> searchDocuments(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK) {

        User user = userService.getCurrentUser()
                .orElseThrow(() -> new IllegalStateException("User not authenticated"));

        if (!documentService.isAvailable()) {
            return ResponseEntity.badRequest().build();
        }

        List<org.springframework.ai.document.Document> results =
                documentService.searchUserDocuments(user.getId(), query, topK);

        List<Map<String, Object>> response = results.stream()
                .map(doc -> Map.<String, Object>of(
                        "content", doc.getText(),
                        "metadata", doc.getMetadata()
                ))
                .toList();

        return ResponseEntity.ok(response);
    }
}
