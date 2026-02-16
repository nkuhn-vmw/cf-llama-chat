package com.example.cfchat.controller;

import com.example.cfchat.auth.UserService;
import com.example.cfchat.model.ChatFolder;
import com.example.cfchat.model.User;
import com.example.cfchat.repository.ChatFolderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatFolderController.class)
@AutoConfigureMockMvc(addFilters = false)
class ChatFolderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ChatFolderRepository folderRepo;

    @MockBean
    private UserService userService;

    @MockBean
    private com.example.cfchat.service.RateLimitService rateLimitService;

    private final UUID userId = UUID.randomUUID();

    private void mockCurrentUser() {
        User user = User.builder().id(userId).username("testuser").build();
        when(userService.getCurrentUser()).thenReturn(Optional.of(user));
    }

    private ChatFolder buildFolder(UUID id, String name, int sortOrder) {
        return ChatFolder.builder()
                .id(id)
                .userId(userId)
                .name(name)
                .sortOrder(sortOrder)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @WithMockUser(username = "testuser")
    void list_returnsFolders() throws Exception {
        mockCurrentUser();
        UUID folderId = UUID.randomUUID();
        ChatFolder folder = buildFolder(folderId, "Work", 0);

        when(folderRepo.findByUserIdOrderBySortOrderAsc(userId)).thenReturn(List.of(folder));

        mockMvc.perform(get("/api/folders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Work"))
                .andExpect(jsonPath("$[0].sortOrder").value(0));
    }

    @Test
    @WithMockUser(username = "testuser")
    void create_validRequest_returnsCreatedFolder() throws Exception {
        mockCurrentUser();
        UUID folderId = UUID.randomUUID();
        ChatFolder folder = buildFolder(folderId, "Projects", 1);

        when(folderRepo.save(any(ChatFolder.class))).thenReturn(folder);

        mockMvc.perform(post("/api/folders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Projects",
                                "sortOrder", 1
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Projects"))
                .andExpect(jsonPath("$.sortOrder").value(1));
    }

    @Test
    @WithMockUser(username = "testuser")
    void update_validRequest_returnsUpdatedFolder() throws Exception {
        mockCurrentUser();
        UUID folderId = UUID.randomUUID();
        ChatFolder existing = buildFolder(folderId, "Old Name", 0);
        ChatFolder updated = buildFolder(folderId, "New Name", 2);

        when(folderRepo.findById(folderId)).thenReturn(Optional.of(existing));
        when(folderRepo.save(any(ChatFolder.class))).thenReturn(updated);

        mockMvc.perform(put("/api/folders/" + folderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "New Name",
                                "sortOrder", 2
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"))
                .andExpect(jsonPath("$.sortOrder").value(2));
    }

    @Test
    @WithMockUser(username = "testuser")
    void delete_validRequest_returns204() throws Exception {
        mockCurrentUser();
        UUID folderId = UUID.randomUUID();
        ChatFolder folder = buildFolder(folderId, "ToDelete", 0);

        when(folderRepo.findById(folderId)).thenReturn(Optional.of(folder));
        doNothing().when(folderRepo).deleteById(folderId);

        mockMvc.perform(delete("/api/folders/" + folderId))
                .andExpect(status().isNoContent());

        verify(folderRepo).deleteById(folderId);
    }
}
