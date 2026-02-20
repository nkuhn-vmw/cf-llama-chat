package com.example.cfchat.repository;

import com.example.cfchat.model.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChannelRepository extends JpaRepository<Channel, UUID> {

    List<Channel> findAllByOrderByCreatedAtDesc();

    List<Channel> findByCreatedByOrderByCreatedAtDesc(UUID createdBy);
}
