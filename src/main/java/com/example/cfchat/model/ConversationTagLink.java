package com.example.cfchat.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "conversation_tag_links")
@IdClass(ConversationTagLinkId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationTagLink {

    @Id
    @Column(name = "conversation_id")
    private UUID conversationId;

    @Id
    @Column(name = "tag_id")
    private Long tagId;
}
