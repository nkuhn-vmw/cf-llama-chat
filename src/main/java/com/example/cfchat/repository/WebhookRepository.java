package com.example.cfchat.repository;

import com.example.cfchat.model.Webhook;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WebhookRepository extends JpaRepository<Webhook, Long> {
    List<Webhook> findByEventTypeAndEnabledTrue(String eventType);
    List<Webhook> findByEnabledTrue();
}
