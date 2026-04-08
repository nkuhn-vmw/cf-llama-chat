package com.example.cfchat.controller;

import com.example.cfchat.auth.UserService;
import com.example.cfchat.dto.wiki.WikiPageView;
import com.example.cfchat.dto.wiki.WikiSearchHit;
import com.example.cfchat.model.User;
import com.example.cfchat.model.wiki.WikiLogEntry;
import com.example.cfchat.model.wiki.WikiPage;
import com.example.cfchat.model.wiki.WikiPageHistory;
import com.example.cfchat.repository.wiki.WikiLogRepository;
import com.example.cfchat.repository.wiki.WikiPageHistoryRepository;
import com.example.cfchat.repository.wiki.WikiPageRepository;
import com.example.cfchat.service.wiki.WikiScope;
import com.example.cfchat.service.wiki.WikiService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/wiki")
public class WikiController {

    private final WikiService wikiService;
    private final UserService userService;
    private final WikiPageRepository pageRepo;
    private final WikiPageHistoryRepository historyRepo;
    private final WikiLogRepository logRepo;

    public WikiController(WikiService wikiService, UserService userService,
                          WikiPageRepository pageRepo,
                          WikiPageHistoryRepository historyRepo,
                          WikiLogRepository logRepo) {
        this.wikiService = wikiService;
        this.userService = userService;
        this.pageRepo = pageRepo;
        this.historyRepo = historyRepo;
        this.logRepo = logRepo;
    }

    private UUID currentUserId() {
        return userService.getCurrentUser()
                .map(User::getId)
                .orElseThrow(() -> new SecurityException("Authentication required"));
    }

    @GetMapping("/pages")
    public List<WikiPage> listPages(
            @RequestParam(required = false) String kind,
            @RequestParam(defaultValue = "100") int limit) {
        UUID userId = currentUserId();
        if (kind != null && !kind.isBlank()) {
            return pageRepo.findByUserIdAndKindOrderByUpdatedAtDesc(userId, kind);
        }
        return pageRepo.findByUserIdOrderByUpdatedAtDesc(userId, PageRequest.of(0, limit));
    }

    @GetMapping("/pages/{id}")
    public WikiPage readPage(@PathVariable UUID id) {
        UUID userId = currentUserId();
        WikiPage p = pageRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Page not found"));
        if (!p.getUserId().equals(userId)) throw new SecurityException("Not owned");
        return p;
    }

    public record UpdatePageRequest(String title, String bodyMd, String kind) {}

    @PutMapping("/pages/{id}")
    public WikiPageView updatePage(@PathVariable UUID id, @RequestBody UpdatePageRequest body) {
        UUID userId = currentUserId();
        WikiPage existing = pageRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Page not found"));
        if (!existing.getUserId().equals(userId)) throw new SecurityException("Not owned");
        return wikiService.upsert(new WikiScope(userId, null),
                existing.getSlug(),
                body.title() == null ? existing.getTitle() : body.title(),
                body.kind() == null ? existing.getKind() : body.kind(),
                body.bodyMd() == null ? existing.getBodyMd() : body.bodyMd(),
                "USER_DIRECT_EDIT");
    }

    @PostMapping("/pages/{id}/undo")
    public WikiPageView undo(@PathVariable UUID id) {
        UUID userId = currentUserId();
        return wikiService.undo(new WikiScope(userId, null), id);
    }

    @GetMapping("/pages/{id}/history")
    public List<WikiPageHistory> history(@PathVariable UUID id) {
        UUID userId = currentUserId();
        WikiPage p = pageRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Page not found"));
        if (!p.getUserId().equals(userId)) throw new SecurityException("Not owned");
        return historyRepo.findByPageIdOrderByVersionDesc(id);
    }

    @GetMapping("/search")
    public List<WikiSearchHit> search(
            @RequestParam("q") String query,
            @RequestParam(required = false) String kind,
            @RequestParam(defaultValue = "6") int k) {
        UUID userId = currentUserId();
        return wikiService.search(new WikiScope(userId, null), query, kind, k);
    }

    @GetMapping("/log")
    public List<WikiLogEntry> log(@RequestParam(defaultValue = "20") int limit) {
        UUID userId = currentUserId();
        return logRepo.findByUserIdOrderByTsDesc(userId, PageRequest.of(0, limit));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> forbidden(SecurityException e) {
        return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> notFound(NoSuchElementException e) {
        return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    }
}
