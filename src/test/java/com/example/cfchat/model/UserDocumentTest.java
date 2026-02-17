package com.example.cfchat.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserDocumentTest {

    @Test
    void builder_defaults_areSet() {
        UserDocument doc = UserDocument.builder()
                .filename("test.pdf")
                .originalFilename("test.pdf")
                .build();

        assertThat(doc.getChunkCount()).isZero();
        assertThat(doc.getStatus()).isEqualTo(UserDocument.DocumentStatus.PENDING);
    }

    @Test
    void documentStatus_values_containsExpected() {
        assertThat(UserDocument.DocumentStatus.values()).containsExactly(
                UserDocument.DocumentStatus.PENDING,
                UserDocument.DocumentStatus.PROCESSING,
                UserDocument.DocumentStatus.COMPLETED,
                UserDocument.DocumentStatus.FAILED
        );
    }

    @Test
    void onCreate_setsTimestamp() {
        UserDocument doc = UserDocument.builder()
                .filename("test.pdf")
                .originalFilename("test.pdf")
                .build();

        doc.onCreate();

        assertThat(doc.getCreatedAt()).isNotNull();
    }

    @Test
    void allFields_setCorrectly() {
        UUID id = UUID.randomUUID();

        UserDocument doc = UserDocument.builder()
                .id(id)
                .filename("abc123.pdf")
                .originalFilename("My Document.pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .chunkCount(5)
                .status(UserDocument.DocumentStatus.COMPLETED)
                .storagePath("s3://bucket/abc123.pdf")
                .build();

        assertThat(doc.getId()).isEqualTo(id);
        assertThat(doc.getFilename()).isEqualTo("abc123.pdf");
        assertThat(doc.getOriginalFilename()).isEqualTo("My Document.pdf");
        assertThat(doc.getContentType()).isEqualTo("application/pdf");
        assertThat(doc.getFileSize()).isEqualTo(1024L);
        assertThat(doc.getChunkCount()).isEqualTo(5);
        assertThat(doc.getStatus()).isEqualTo(UserDocument.DocumentStatus.COMPLETED);
        assertThat(doc.getStoragePath()).isEqualTo("s3://bucket/abc123.pdf");
    }

    @Test
    void errorMessage_canBeSet() {
        UserDocument doc = UserDocument.builder()
                .filename("bad.pdf")
                .originalFilename("bad.pdf")
                .status(UserDocument.DocumentStatus.FAILED)
                .errorMessage("Failed to parse document")
                .build();

        assertThat(doc.getErrorMessage()).isEqualTo("Failed to parse document");
    }

    @Test
    void shared_defaultsToNull() {
        UserDocument doc = UserDocument.builder()
                .filename("test.pdf")
                .originalFilename("test.pdf")
                .build();

        assertThat(doc.getShared()).isNull();
    }

    @Test
    void shared_canBeSetToTrue() {
        UserDocument doc = UserDocument.builder()
                .filename("test.pdf")
                .originalFilename("test.pdf")
                .shared(Boolean.TRUE)
                .build();

        assertThat(doc.getShared()).isTrue();
    }

    @Test
    void shared_canBeSetToFalse() {
        UserDocument doc = UserDocument.builder()
                .filename("test.pdf")
                .originalFilename("test.pdf")
                .shared(Boolean.FALSE)
                .build();

        assertThat(doc.getShared()).isFalse();
    }
}
