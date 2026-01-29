package com.example.cfchat.dto;

import com.example.cfchat.model.Conversation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationDto {
    private UUID id;
    private String title;
    private String modelProvider;
    private String modelName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<MessageDto> messages;
    private int messageCount;

    public static ConversationDto fromEntity(Conversation conversation, boolean includeMessages) {
        ConversationDtoBuilder builder = ConversationDto.builder()
                .id(conversation.getId())
                .title(conversation.getTitle())
                .modelProvider(conversation.getModelProvider())
                .modelName(conversation.getModelName())
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .messageCount(conversation.getMessages().size());

        if (includeMessages) {
            builder.messages(conversation.getMessages().stream()
                    .map(MessageDto::fromEntity)
                    .toList());
        }

        return builder.build();
    }
}
