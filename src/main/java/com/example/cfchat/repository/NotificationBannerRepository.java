package com.example.cfchat.repository;

import com.example.cfchat.model.NotificationBanner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface NotificationBannerRepository extends JpaRepository<NotificationBanner, Long> {
    @Query("SELECT b FROM NotificationBanner b WHERE b.active = true AND (b.expiresAt IS NULL OR b.expiresAt > CURRENT_TIMESTAMP)")
    List<NotificationBanner> findActiveBanners();
}
