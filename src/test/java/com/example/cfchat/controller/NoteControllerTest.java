package com.example.cfchat.controller;

import com.example.cfchat.auth.UserService;
import com.example.cfchat.model.User;
import com.example.cfchat.model.UserNote;
import com.example.cfchat.repository.UserNoteRepository;
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

@WebMvcTest(NoteController.class)
@AutoConfigureMockMvc(addFilters = false)
class NoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserNoteRepository noteRepo;

    @MockBean
    private UserService userService;

    @MockBean
    private com.example.cfchat.service.RateLimitService rateLimitService;

    private final UUID userId = UUID.randomUUID();

    private void mockCurrentUser() {
        User user = User.builder().id(userId).username("testuser").build();
        when(userService.getCurrentUser()).thenReturn(Optional.of(user));
    }

    private UserNote buildNote(UUID id, String title, String content) {
        LocalDateTime now = LocalDateTime.now();
        return UserNote.builder()
                .id(id)
                .userId(userId)
                .title(title)
                .content(content)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    @Test
    @WithMockUser(username = "testuser")
    void list_returnsNotes() throws Exception {
        mockCurrentUser();
        UUID noteId = UUID.randomUUID();
        UserNote note = buildNote(noteId, "My Note", "Some content");

        when(noteRepo.findByUserIdOrderByUpdatedAtDesc(userId)).thenReturn(List.of(note));

        mockMvc.perform(get("/api/notes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("My Note"))
                .andExpect(jsonPath("$[0].content").value("Some content"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void create_validRequest_returnsCreatedNote() throws Exception {
        mockCurrentUser();
        UUID noteId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        UserNote note = buildNote(noteId, "New Note", "Note content");
        note.setConversationId(convId);

        when(noteRepo.save(any(UserNote.class))).thenReturn(note);

        mockMvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "New Note",
                                "content", "Note content",
                                "conversationId", convId.toString()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New Note"))
                .andExpect(jsonPath("$.content").value("Note content"))
                .andExpect(jsonPath("$.conversationId").value(convId.toString()));
    }

    @Test
    @WithMockUser(username = "testuser")
    void update_validRequest_returnsUpdatedNote() throws Exception {
        mockCurrentUser();
        UUID noteId = UUID.randomUUID();
        UserNote existing = buildNote(noteId, "Old Title", "Old content");
        UserNote updated = buildNote(noteId, "Updated Title", "Updated content");

        when(noteRepo.findById(noteId)).thenReturn(Optional.of(existing));
        when(noteRepo.save(any(UserNote.class))).thenReturn(updated);

        mockMvc.perform(put("/api/notes/" + noteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Updated Title",
                                "content", "Updated content"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Title"))
                .andExpect(jsonPath("$.content").value("Updated content"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void delete_validRequest_returns204() throws Exception {
        mockCurrentUser();
        UUID noteId = UUID.randomUUID();
        UserNote note = buildNote(noteId, "ToDelete", "Content");

        when(noteRepo.findById(noteId)).thenReturn(Optional.of(note));
        doNothing().when(noteRepo).deleteById(noteId);

        mockMvc.perform(delete("/api/notes/" + noteId))
                .andExpect(status().isNoContent());

        verify(noteRepo).deleteById(noteId);
    }
}
