package com.example.cfchat.controller;

import com.example.cfchat.model.NotificationBanner;
import com.example.cfchat.repository.NotificationBannerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class BannerController {

    private final NotificationBannerRepository bannerRepo;

    @GetMapping("/api/banners")
    public ResponseEntity<List<BannerDto>> activeBanners() {
        return ResponseEntity.ok(bannerRepo.findActiveBanners().stream()
                .map(b -> new BannerDto(b.getId(), b.getContent(), b.getBannerType(), b.isDismissible())).toList());
    }

    @PostMapping("/api/admin/banners")
    public ResponseEntity<BannerDto> create(@RequestBody CreateBannerRequest req) {
        NotificationBanner banner = NotificationBanner.builder()
                .content(req.content()).bannerType(req.bannerType())
                .dismissible(req.dismissible() != null ? req.dismissible() : true)
                .expiresAt(req.expiresAt()).build();
        banner = bannerRepo.save(banner);
        return ResponseEntity.ok(new BannerDto(banner.getId(), banner.getContent(), banner.getBannerType(), banner.isDismissible()));
    }

    @DeleteMapping("/api/admin/banners/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        bannerRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    public record BannerDto(Long id, String content, String bannerType, boolean dismissible) {}
    public record CreateBannerRequest(String content, String bannerType, Boolean dismissible, java.time.LocalDateTime expiresAt) {}
}
