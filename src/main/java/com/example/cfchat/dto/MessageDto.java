package com.example.cfchat.dto;

import com.example.cfchat.model.Message;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageDto {
    private UUID id;
    private String role;
    private String content;
    private String htmlContent;
    private String modelUsed;
    private LocalDateTime createdAt;

    public static MessageDto fromEntity(Message message) {
        return MessageDto.builder()
                .id(message.getId())
                .role(message.getRole().name().toLowerCase())
                .content(message.getContent())
                .modelUsed(message.getModelUsed())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
