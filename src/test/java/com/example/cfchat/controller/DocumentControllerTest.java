package com.example.cfchat.controller;

import com.example.cfchat.auth.UserService;
import com.example.cfchat.dto.DocumentUploadResponse;
import com.example.cfchat.dto.UserDocumentDto;
import com.example.cfchat.model.User;
import com.example.cfchat.model.UserDocument;
import com.example.cfchat.service.DocumentEmbeddingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentEmbeddingService documentService;

    @MockBean
    private UserService userService;

    @MockBean
    private com.example.cfchat.service.RateLimitService rateLimitService;

    @Test
    @WithMockUser(username = "testuser")
    void getStatus_available_returnsAvailable() throws Exception {
        when(documentService.isAvailable()).thenReturn(true);

        mockMvc.perform(get("/api/documents/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    @WithMockUser(username = "testuser")
    void getStatus_unavailable_returnsUnavailable() throws Exception {
        when(documentService.isAvailable()).thenReturn(false);

        mockMvc.perform(get("/api/documents/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    @WithMockUser(username = "testuser")
    void uploadDocument_serviceUnavailable_returnsBadRequest() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).username("testuser").build();
        when(userService.getCurrentUser()).thenReturn(Optional.of(user));
        when(documentService.isAvailable()).thenReturn(false);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "content".getBytes());

        mockMvc.perform(multipart("/api/documents/upload").file(file)
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void uploadDocument_success_returnsResponse() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).username("testuser").build();
        when(userService.getCurrentUser()).thenReturn(Optional.of(user));
        when(documentService.isAvailable()).thenReturn(true);

        DocumentUploadResponse response = DocumentUploadResponse.builder()
                .filename("test.pdf")
                .status("SUCCESS")
                .message("Document uploaded and embedded")
                .build();
        when(documentService.uploadDocument(eq(userId), any())).thenReturn(response);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "content".getBytes());

        mockMvc.perform(multipart("/api/documents/upload").file(file)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void getUserDocuments_returnsDocumentList() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).username("testuser").build();
        when(userService.getCurrentUser()).thenReturn(Optional.of(user));

        UserDocumentDto doc = UserDocumentDto.builder()
                .id(UUID.randomUUID())
                .originalFilename("test.pdf")
                .status(UserDocument.DocumentStatus.COMPLETED)
                .build();
        when(documentService.getUserDocuments(userId)).thenReturn(List.of(doc));

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].originalFilename").value("test.pdf"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void deleteDocument_success_returnsOk() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        User user = User.builder().id(userId).username("testuser").build();
        when(userService.getCurrentUser()).thenReturn(Optional.of(user));
        when(documentService.deleteDocument(userId, docId)).thenReturn(true);

        mockMvc.perform(delete("/api/documents/" + docId).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "testuser")
    void deleteDocument_notFound_returns404() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        User user = User.builder().id(userId).username("testuser").build();
        when(userService.getCurrentUser()).thenReturn(Optional.of(user));
        when(documentService.deleteDocument(userId, docId)).thenReturn(false);

        mockMvc.perform(delete("/api/documents/" + docId).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "testuser")
    void deleteAllDocuments_success_returnsOk() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).username("testuser").build();
        when(userService.getCurrentUser()).thenReturn(Optional.of(user));

        mockMvc.perform(delete("/api/documents").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "testuser")
    void getDocumentStats_returnsStats() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).username("testuser").build();
        when(userService.getCurrentUser()).thenReturn(Optional.of(user));
        when(documentService.getUserDocumentStats(userId)).thenReturn(Map.of(
                "totalDocuments", 5,
                "totalChunks", 50
        ));

        mockMvc.perform(get("/api/documents/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDocuments").value(5));
    }

    @Test
    void getStatus_unauthenticated_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/documents/status"))
                .andExpect(status().isUnauthorized());
    }
}
