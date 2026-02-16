package com.example.cfchat.repository;

import com.example.cfchat.model.ChannelMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChannelMessageRepository extends JpaRepository<ChannelMessage, UUID> {

    List<ChannelMessage> findByChannelIdOrderByCreatedAtAsc(UUID channelId);

    List<ChannelMessage> findByChannelIdOrderByCreatedAtDesc(UUID channelId, Pageable pageable);

    long countByChannelId(UUID channelId);
}
