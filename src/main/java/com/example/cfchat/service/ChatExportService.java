package com.example.cfchat.service;

import com.example.cfchat.model.Conversation;
import com.example.cfchat.model.Message;
import com.example.cfchat.repository.ConversationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatExportService {

    private final ConversationRepository convRepo;
    private final ConversationService convService;
    private final ObjectMapper mapper;

    // Export DTOs
    public record ExportBundle(String version, String source, Instant exportedAt, List<ConversationExport> conversations) {}
    public record ConversationExport(String id, String title, String modelName, LocalDateTime createdAt, List<MessageExport> messages) {}
    public record MessageExport(String role, String content, String modelUsed, Integer tokensUsed, LocalDateTime createdAt) {}

    @Transactional(readOnly = true)
    public byte[] exportJson(UUID conversationId, UUID userId) throws Exception {
        Conversation c = getOwnedConversation(conversationId, userId);
        ExportBundle bundle = new ExportBundle("1.0", "cf-llama-chat", Instant.now(), List.of(toExport(c)));
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(bundle);
    }

    @Transactional(readOnly = true)
    public byte[] exportTxt(UUID conversationId, UUID userId) {
        Conversation c = getOwnedConversation(conversationId, userId);
        StringBuilder sb = new StringBuilder("# " + c.getTitle() + "\n\n");
        if (c.getMessages() != null) {
            c.getMessages().stream()
                    .filter(m -> Boolean.TRUE.equals(m.getActive()))
                    .forEach(m -> sb.append(m.getRole().name()).append(":\n").append(m.getContent()).append("\n\n"));
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public byte[] exportAllJson(UUID userId) throws Exception {
        List<Conversation> all = convRepo.findByUserIdOrderByUpdatedAtDesc(userId);
        List<ConversationExport> exports = all.stream().map(this::toExport).toList();
        ExportBundle bundle = new ExportBundle("1.0", "cf-llama-chat", Instant.now(), exports);
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(bundle);
    }

    @Transactional
    public List<UUID> importJson(byte[] data, UUID userId) throws Exception {
        ExportBundle bundle = mapper.readValue(data, ExportBundle.class);
        return bundle.conversations().stream().map(dto -> {
            Conversation c = convService.createConversation(
                    dto.title() + " (imported)", null, dto.modelName(), userId);
            if (dto.messages() != null) {
                dto.messages().forEach(m -> convService.addMessage(
                        c.getId(), Message.MessageRole.valueOf(m.role()), m.content(), m.modelUsed()));
            }
            return c.getId();
        }).toList();
    }

    private Conversation getOwnedConversation(UUID convId, UUID userId) {
        return convService.getConversationEntityForUser(convId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found or access denied"));
    }

    private ConversationExport toExport(Conversation c) {
        List<MessageExport> msgs = c.getMessages() != null ? c.getMessages().stream()
                .filter(m -> Boolean.TRUE.equals(m.getActive()))
                .map(m -> new MessageExport(m.getRole().name(), m.getContent(), m.getModelUsed(), m.getTokensUsed(), m.getCreatedAt()))
                .toList() : List.of();
        return new ConversationExport(c.getId().toString(), c.getTitle(), c.getModelName(), c.getCreatedAt(), msgs);
    }
}
