package com.example.cfchat.service;

import com.example.cfchat.model.Channel;
import com.example.cfchat.model.ChannelMessage;
import com.example.cfchat.model.Message;
import com.example.cfchat.repository.ChannelMessageRepository;
import com.example.cfchat.repository.ChannelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChannelService {

    private final ChannelRepository channelRepository;
    private final ChannelMessageRepository channelMessageRepository;

    @Transactional
    public Channel createChannel(String name, String description, String modelId, UUID createdBy) {
        Channel channel = Channel.builder()
                .name(name)
                .description(description)
                .modelId(modelId)
                .createdBy(createdBy)
                .build();
        Channel saved = channelRepository.save(channel);
        log.info("Created channel '{}' (id: {}) by user {}", name, saved.getId(), createdBy);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Channel> listChannels() {
        return channelRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Optional<Channel> getChannel(UUID id) {
        return channelRepository.findById(id);
    }

    @Transactional
    public boolean deleteChannel(UUID channelId, UUID userId, boolean isAdmin) {
        Optional<Channel> channelOpt = channelRepository.findById(channelId);
        if (channelOpt.isEmpty()) {
            return false;
        }
        Channel channel = channelOpt.get();
        if (!isAdmin && !userId.equals(channel.getCreatedBy())) {
            return false;
        }
        channelRepository.delete(channel);
        log.info("Deleted channel '{}' (id: {}) by user {}", channel.getName(), channelId, userId);
        return true;
    }

    @Transactional(readOnly = true)
    public List<ChannelMessage> getMessages(UUID channelId) {
        return channelMessageRepository.findByChannelIdOrderByCreatedAtAsc(channelId);
    }

    @Transactional(readOnly = true)
    public List<ChannelMessage> getRecentMessages(UUID channelId, int limit) {
        List<ChannelMessage> messages = channelMessageRepository
                .findByChannelIdOrderByCreatedAtDesc(channelId, PageRequest.of(0, limit));
        // Reverse to get ascending order
        return messages.reversed();
    }

    @Transactional
    public ChannelMessage addMessage(UUID channelId, UUID userId, String username,
                                     String content, Message.MessageRole role) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channelId));

        ChannelMessage message = ChannelMessage.builder()
                .channel(channel)
                .userId(userId)
                .username(username)
                .content(content)
                .role(role)
                .build();

        return channelMessageRepository.save(message);
    }
}
