package com.example.cfchat.service;

import com.example.cfchat.config.VectorStoreConfig;
import com.example.cfchat.dto.DocumentUploadResponse;
import com.example.cfchat.dto.UserDocumentDto;
import com.example.cfchat.model.EmbeddingMetric.OperationType;
import com.example.cfchat.model.User;
import com.example.cfchat.model.UserDocument;
import com.example.cfchat.model.UserDocument.DocumentStatus;
import com.example.cfchat.repository.UserDocumentRepository;
import com.example.cfchat.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for handling document uploads, embedding generation, and vector store operations.
 * Each user has their own document space, with embeddings tagged by user ID for filtering.
 */
@Service
@Slf4j
public class DocumentEmbeddingService {

    private final VectorStore vectorStore;
    private final UserDocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final DocumentStorageService storageService;
    private final MetricsService metricsService;
    private final VectorStoreConfig vectorStoreConfig;

    @Value("${app.documents.max-file-size:10485760}")  // 10MB default
    private long maxFileSize;

    @Value("${app.documents.max-documents-per-user:50}")
    private int maxDocumentsPerUser;

    @Value("${app.documents.chunk-size:400}")
    private int chunkSize;

    @Value("${app.documents.chunk-overlap:100}")
    private int chunkOverlap;

    private TokenTextSplitter textSplitter;

    public DocumentEmbeddingService(
            @Autowired(required = false) VectorStore vectorStore,
            UserDocumentRepository documentRepository,
            UserRepository userRepository,
            JdbcTemplate jdbcTemplate,
            DocumentStorageService storageService,
            MetricsService metricsService,
            @Autowired(required = false) VectorStoreConfig vectorStoreConfig) {
        this.vectorStore = vectorStore;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.storageService = storageService;
        this.metricsService = metricsService;
        this.vectorStoreConfig = vectorStoreConfig;
    }

    @PostConstruct
    public void init() {
        // Initialize text splitter with injected config values
        // Chunk size should stay under embedding model's token limit (512 for nomic)
        // Use 350 to leave room for overhead and special tokens
        int effectiveChunkSize = Math.min(chunkSize, 350);
        this.textSplitter = TokenTextSplitter.builder()
                .withChunkSize(effectiveChunkSize)
                .withMinChunkSizeChars(100)  // Lower threshold to capture more content
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(10000)
                .withKeepSeparator(true)
                .build();

        log.info("DocumentEmbeddingService initialized - vectorStore: {}, chunkSize: {}, overlap: {}",
                vectorStore != null ? vectorStore.getClass().getSimpleName() : "null",
                effectiveChunkSize, chunkOverlap);

        // Run database migrations
        migrateErrorMessageColumn();
    }

    /**
     * Migrate the error_message column to TEXT type to support longer error messages.
     * This is idempotent - safe to run multiple times.
     */
    private void migrateErrorMessageColumn() {
        try {
            jdbcTemplate.execute("ALTER TABLE user_documents ALTER COLUMN error_message TYPE TEXT");
            log.info("Successfully migrated error_message column to TEXT type");
        } catch (Exception e) {
            // Column may already be TEXT or table doesn't exist yet - that's fine
            log.debug("Migration of error_message column skipped: {}", e.getMessage());
        }

        // Migrate vector dimensions from 512 to 768 for nomic model compatibility
        // Only truncate if the ALTER TABLE actually succeeds (dimensions need changing)
        try {
            jdbcTemplate.execute("ALTER TABLE document_embeddings ALTER COLUMN embedding TYPE vector(768)");
            // ALTER succeeded, so dimensions were different - existing embeddings are incompatible
            log.warn("Embedding dimensions changed to 768 - clearing incompatible embeddings");
            jdbcTemplate.execute("TRUNCATE TABLE document_embeddings");
            log.info("Successfully migrated embedding column to 768 dimensions");
        } catch (Exception e) {
            // Table may not exist yet or already has correct dimensions - no truncation needed
            log.debug("Migration of embedding dimensions skipped: {}", e.getMessage());
        }
    }

    /**
     * Check if the embedding service is available.
     */
    public boolean isAvailable() {
        return vectorStore != null;
    }

    /**
     * Upload and process a document for a user.
     * Note: Not using @Transactional here so that document status can be updated
     * to FAILED even if the embedding operation fails.
     */
    public DocumentUploadResponse uploadDocument(UUID userId, MultipartFile file) throws IOException {
        long startTime = System.currentTimeMillis();

        // Validate user exists
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Validate file
        validateFile(file, userId);

        // Create document record
        String filename = UUID.randomUUID().toString() + "_" + sanitizeFilename(file.getOriginalFilename());
        UserDocument document = UserDocument.builder()
                .user(user)
                .filename(filename)
                .originalFilename(file.getOriginalFilename())
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .status(DocumentStatus.PROCESSING)
                .build();

        document = documentRepository.save(document);
        log.info("Created document record: {} for user: {}", document.getId(), userId);

        try {
            // Store original document in S3 if enabled
            if (storageService.isStorageEnabled()) {
                try {
                    String storagePath = storageService.storeDocument(userId, document.getId(), file);
                    document.setStoragePath(storagePath);
                    log.info("Stored original document in S3: {}", storagePath);
                } catch (Exception e) {
                    log.warn("Failed to store document in S3, continuing with embedding only: {}", e.getMessage());
                }
            }

            // Read and chunk the document
            List<Document> documents = readAndChunkDocument(file, document.getId(), userId);

            if (documents.isEmpty()) {
                throw new RuntimeException("No content could be extracted from the document");
            }

            // Calculate total characters for metrics
            long totalCharacters = documents.stream()
                    .mapToLong(doc -> doc.getText() != null ? doc.getText().length() : 0)
                    .sum();

            // Store chunks in vector store (this triggers the embedding)
            long embeddingStartTime = System.currentTimeMillis();
            vectorStore.accept(documents);
            long embeddingTime = System.currentTimeMillis() - embeddingStartTime;

            // Update document status
            document.setStatus(DocumentStatus.COMPLETED);
            document.setChunkCount(documents.size());
            document.setProcessedAt(LocalDateTime.now());
            documentRepository.save(document);

            long processingTime = System.currentTimeMillis() - startTime;
            log.info("Successfully processed document {} with {} chunks in {}ms (embedding: {}ms)",
                    document.getId(), documents.size(), processingTime, embeddingTime);

            // Record embedding metrics
            String embeddingModel = getEmbeddingModelName();
            metricsService.recordEmbeddingUsage(
                    userId,
                    document.getId(),
                    embeddingModel,
                    documents.size(),
                    totalCharacters,
                    embeddingTime,
                    OperationType.DOCUMENT_UPLOAD
            );

            return DocumentUploadResponse.builder()
                    .documentId(document.getId())
                    .filename(file.getOriginalFilename())
                    .status("COMPLETED")
                    .message("Document processed successfully")
                    .chunkCount(documents.size())
                    .processingTimeMs(processingTime)
                    .build();

        } catch (Exception e) {
            log.error("Failed to process document {}: {}", document.getId(), e.getMessage(), e);

            document.setStatus(DocumentStatus.FAILED);
            document.setErrorMessage(truncateErrorMessage(e.getMessage()));
            documentRepository.save(document);

            return DocumentUploadResponse.builder()
                    .documentId(document.getId())
                    .filename(file.getOriginalFilename())
                    .status("FAILED")
                    .message("Failed to process document: " + e.getMessage())
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    /**
     * Read a document and split it into chunks.
     * Uses streaming to avoid loading large files entirely into memory.
     */
    private List<Document> readAndChunkDocument(MultipartFile file, UUID documentId, UUID userId) throws IOException {
        List<Document> documents;

        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();

        if (isPdf(contentType, filename)) {
            // Use PagePdfDocumentReader for PDF files - stream directly from MultipartFile
            InputStreamResource resource = new InputStreamResource(file.getInputStream());
            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource);
            documents = pdfReader.read();
            log.info("Read {} pages from PDF: {}", documents.size(), filename);
        } else {
            // Use Tika for other file types (Word, text, etc.) - stream directly
            InputStreamResource resource = new InputStreamResource(file.getInputStream());
            TikaDocumentReader tikaReader = new TikaDocumentReader(resource);
            documents = tikaReader.read();
            log.info("Read document using Tika: {}", filename);
        }

        // Split documents into chunks
        List<Document> chunkedDocuments = textSplitter.apply(documents);

        // Add metadata to each chunk
        List<Document> enrichedDocuments = new ArrayList<>();
        int chunkIndex = 0;
        for (Document doc : chunkedDocuments) {
            String content = doc.getText();

            // Clean the content
            content = cleanContent(content);
            if (content.isEmpty()) {
                continue;
            }

            // Store clean content without source markers - source info goes in metadata only
            Document enrichedDoc = new Document(content);
            enrichedDoc.getMetadata().put("user_id", userId.toString());
            enrichedDoc.getMetadata().put("document_id", documentId.toString());
            enrichedDoc.getMetadata().put("filename", filename);
            enrichedDoc.getMetadata().put("chunk_index", chunkIndex);
            enrichedDoc.getMetadata().put("chunk_total", chunkedDocuments.size());
            enrichedDoc.getMetadata().put("content_type", contentType);

            // Copy original metadata
            doc.getMetadata().forEach((key, value) -> {
                if (!enrichedDoc.getMetadata().containsKey(key)) {
                    enrichedDoc.getMetadata().put(key, value);
                }
            });

            enrichedDocuments.add(enrichedDoc);
            chunkIndex++;
        }

        log.info("Created {} chunks from document: {}", enrichedDocuments.size(), filename);
        return enrichedDocuments;
    }

    /**
     * Clean extracted text content.
     */
    private String cleanContent(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }

        // Remove binary/base64 patterns
        String cleaned = content.replaceAll("data:image/[^;]+;base64,[A-Za-z0-9+/=\\s]+", "[IMAGE]");

        // Remove non-printable characters
        cleaned = cleaned.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]+", " ");

        // Normalize whitespace
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        return cleaned;
    }

    /**
     * Search for relevant document chunks for a user's query.
     */
    public List<Document> searchUserDocuments(UUID userId, String query, int topK) {
        if (vectorStore == null) {
            log.warn("VectorStore not available - cannot search documents");
            return List.of();
        }

        log.debug("Searching documents for user {} with query: {}", userId, query);

        // Build search request with user filter
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(topK * 2)  // Get more results to filter
                .build();

        List<Document> results = vectorStore.similaritySearch(searchRequest);

        // Filter results by user ID
        List<Document> userResults = results.stream()
                .filter(doc -> userId.toString().equals(doc.getMetadata().get("user_id")))
                .limit(topK)
                .collect(Collectors.toList());

        log.debug("Found {} relevant chunks for user {}", userResults.size(), userId);
        return userResults;
    }

    /**
     * Get all documents for a user.
     */
    public List<UserDocumentDto> getUserDocuments(UUID userId) {
        return documentRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(UserDocumentDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Get a specific document for a user.
     */
    public Optional<UserDocumentDto> getUserDocument(UUID userId, UUID documentId) {
        return documentRepository.findByIdAndUserId(documentId, userId)
                .map(UserDocumentDto::fromEntity);
    }

    /**
     * Get the original document content from S3 storage.
     *
     * @param userId     The user's ID
     * @param documentId The document's ID
     * @return InputStream of the document content, or empty if not stored
     */
    public Optional<InputStream> getDocumentContent(UUID userId, UUID documentId) throws IOException {
        Optional<UserDocument> docOpt = documentRepository.findByIdAndUserId(documentId, userId);

        if (docOpt.isEmpty()) {
            return Optional.empty();
        }

        UserDocument document = docOpt.get();

        if (document.getStoragePath() == null) {
            return Optional.empty();
        }

        if (!storageService.isStorageEnabled()) {
            return Optional.empty();
        }

        return Optional.of(storageService.getDocument(document.getStoragePath()));
    }

    /**
     * Get the raw UserDocument entity for a user's document.
     */
    public Optional<UserDocument> getUserDocumentEntity(UUID userId, UUID documentId) {
        return documentRepository.findByIdAndUserId(documentId, userId);
    }

    /**
     * Delete a document and its embeddings for a user.
     */
    @Transactional
    public boolean deleteDocument(UUID userId, UUID documentId) {
        Optional<UserDocument> docOpt = documentRepository.findByIdAndUserId(documentId, userId);

        if (docOpt.isEmpty()) {
            return false;
        }

        UserDocument document = docOpt.get();

        // Delete embeddings from vector store
        try {
            deleteDocumentEmbeddings(documentId);
        } catch (Exception e) {
            log.warn("Failed to delete embeddings for document {}: {}", documentId, e.getMessage());
        }

        // Delete from S3 if stored
        if (document.getStoragePath() != null) {
            try {
                storageService.deleteDocument(document.getStoragePath());
            } catch (Exception e) {
                log.warn("Failed to delete document from S3: {}", e.getMessage());
            }
        }

        // Delete document record
        documentRepository.delete(document);
        log.info("Deleted document {} for user {}", documentId, userId);

        return true;
    }

    /**
     * Delete all documents and embeddings for a user.
     */
    @Transactional
    public void deleteAllUserDocuments(UUID userId) {
        List<UserDocument> documents = documentRepository.findByUserIdOrderByCreatedAtDesc(userId);

        for (UserDocument doc : documents) {
            try {
                deleteDocumentEmbeddings(doc.getId());
            } catch (Exception e) {
                log.warn("Failed to delete embeddings for document {}: {}", doc.getId(), e.getMessage());
            }

            // Delete from S3 if stored
            if (doc.getStoragePath() != null) {
                try {
                    storageService.deleteDocument(doc.getStoragePath());
                } catch (Exception e) {
                    log.warn("Failed to delete document from S3: {}", e.getMessage());
                }
            }
        }

        documentRepository.deleteByUserId(userId);
        log.info("Deleted all documents for user {}", userId);
    }

    /**
     * Delete embeddings for a specific document from the vector store.
     */
    private void deleteDocumentEmbeddings(UUID documentId) {
        // Use JDBC to delete embeddings with matching document_id in metadata
        String sql = """
            DELETE FROM document_embeddings
            WHERE metadata::jsonb ->> 'document_id' = ?
            """;
        int deleted = jdbcTemplate.update(sql, documentId.toString());
        log.debug("Deleted {} embeddings for document {}", deleted, documentId);
    }

    /**
     * Get document statistics for a user.
     */
    public Map<String, Object> getUserDocumentStats(UUID userId) {
        long totalDocuments = documentRepository.countByUserId(userId);
        long completedDocuments = documentRepository.countByUserIdAndStatus(userId, DocumentStatus.COMPLETED);
        long totalStorage = documentRepository.getTotalStorageByUserId(userId);

        // Count total chunks
        int totalChunks = documentRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .mapToInt(doc -> doc.getChunkCount() != null ? doc.getChunkCount() : 0)
                .sum();

        return Map.of(
                "totalDocuments", totalDocuments,
                "completedDocuments", completedDocuments,
                "totalStorageBytes", totalStorage,
                "totalChunks", totalChunks,
                "maxDocuments", maxDocumentsPerUser,
                "maxFileSizeBytes", maxFileSize
        );
    }

    /**
     * Validate an uploaded file.
     */
    private void validateFile(MultipartFile file, UUID userId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException(
                    String.format("File size exceeds maximum allowed (%d bytes)", maxFileSize));
        }

        long documentCount = documentRepository.countByUserId(userId);
        if (documentCount >= maxDocumentsPerUser) {
            throw new IllegalArgumentException(
                    String.format("Maximum number of documents (%d) reached", maxDocumentsPerUser));
        }

        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();
        if (!isAllowedFileType(contentType, filename)) {
            throw new IllegalArgumentException("File type not allowed. Supported: PDF, Word, Text, Markdown");
        }
    }

    /**
     * Check if the file type is allowed.
     */
    private boolean isAllowedFileType(String contentType, String filename) {
        if (contentType == null && filename == null) {
            return false;
        }

        // Check by content type
        if (contentType != null) {
            if (contentType.equals("application/pdf") ||
                    contentType.equals("application/msword") ||
                    contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document") ||
                    contentType.startsWith("text/")) {
                return true;
            }
        }

        // Check by extension
        if (filename != null) {
            String lowerFilename = filename.toLowerCase();
            return lowerFilename.endsWith(".pdf") ||
                    lowerFilename.endsWith(".doc") ||
                    lowerFilename.endsWith(".docx") ||
                    lowerFilename.endsWith(".txt") ||
                    lowerFilename.endsWith(".md") ||
                    lowerFilename.endsWith(".markdown");
        }

        return false;
    }

    /**
     * Check if the file is a PDF.
     */
    private boolean isPdf(String contentType, String filename) {
        if ("application/pdf".equals(contentType)) {
            return true;
        }
        if (filename != null && filename.toLowerCase().endsWith(".pdf")) {
            return true;
        }
        return false;
    }

    /**
     * Sanitize filename for storage.
     */
    private String sanitizeFilename(String filename) {
        if (filename == null) {
            return "document";
        }
        // Remove path separators and limit length
        return filename.replaceAll("[/\\\\]", "_")
                .replaceAll("[^a-zA-Z0-9._-]", "_")
                .substring(0, Math.min(filename.length(), 100));
    }

    /**
     * Truncate error message to fit in database column.
     */
    private String truncateErrorMessage(String message) {
        if (message == null) {
            return null;
        }
        // Truncate to 1000 chars to be safe, even though column is now TEXT
        return message.length() > 1000 ? message.substring(0, 1000) + "..." : message;
    }

    /**
     * Get the name of the active embedding model for metrics tracking.
     */
    private String getEmbeddingModelName() {
        if (vectorStoreConfig != null && vectorStoreConfig.getActiveEmbeddingModel() != null) {
            return vectorStoreConfig.getActiveEmbeddingModel().modelName();
        }
        return "unknown";
    }
}
