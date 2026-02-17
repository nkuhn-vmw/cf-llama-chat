package com.example.cfchat.controller;

import com.example.cfchat.auth.UserService;
import com.example.cfchat.dto.OrganizationThemeDto;
import com.example.cfchat.model.User;
import com.example.cfchat.service.ChatService;
import com.example.cfchat.service.ConversationService;
import com.example.cfchat.service.OrganizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.*;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebController {

    private final ConversationService conversationService;
    private final ChatService chatService;
    private final UserService userService;
    private final OrganizationService organizationService;
    private final ObjectMapper objectMapper;

    private String buildAppDataJson(UUID conversationId, User currentUser) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("conversationId", conversationId);
            data.put("models", chatService.getAvailableModels());
            if (currentUser != null) {
                Map<String, Object> userMap = new LinkedHashMap<>();
                userMap.put("id", currentUser.getId());
                userMap.put("username", currentUser.getUsername());
                userMap.put("email", currentUser.getEmail());
                userMap.put("displayName", currentUser.getDisplayName());
                userMap.put("role", currentUser.getRole());
                data.put("currentUser", userMap);
            }
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.error("Failed to serialize app data", e);
            return "{}";
        }
    }

    @GetMapping("/")
    public String index(Model model) {
        Optional<User> currentUser = userService.getCurrentUser();
        OrganizationThemeDto orgTheme = OrganizationThemeDto.createDefaultTheme();

        if (currentUser.isPresent()) {
            UUID userId = currentUser.get().getId();
            model.addAttribute("conversations", conversationService.getConversationsForUser(userId));
            model.addAttribute("currentUser", currentUser.get());
            orgTheme = organizationService.getThemeForUser(currentUser.get());
        } else {
            model.addAttribute("conversations", conversationService.getAllConversations());
        }

        model.addAttribute("models", chatService.getAvailableModels());
        model.addAttribute("orgTheme", orgTheme);
        model.addAttribute("appDataJson", buildAppDataJson(null, currentUser.orElse(null)));
        return "index";
    }

    @GetMapping("/{slug:[a-z0-9\\-]+}")
    public String orgIndex(@PathVariable String slug, Model model) {
        // Check if this is a valid organization slug
        Optional<OrganizationThemeDto> orgTheme = organizationService.getThemeBySlugOptional(slug);
        if (orgTheme.isEmpty()) {
            // Not a valid organization slug - return 404
            return "error/404";
        }

        Optional<User> currentUser = userService.getCurrentUser();

        if (currentUser.isPresent()) {
            UUID userId = currentUser.get().getId();
            model.addAttribute("conversations", conversationService.getConversationsForUser(userId));
            model.addAttribute("currentUser", currentUser.get());
        } else {
            model.addAttribute("conversations", conversationService.getAllConversations());
        }

        model.addAttribute("models", chatService.getAvailableModels());
        model.addAttribute("orgTheme", orgTheme.get());
        model.addAttribute("orgSlug", slug);
        model.addAttribute("appDataJson", buildAppDataJson(null, currentUser.orElse(null)));
        return "index";
    }

    @GetMapping("/{slug:[a-z0-9\\-]+}/chat/{id}")
    public String orgChat(@PathVariable String slug, @PathVariable UUID id, Model model) {
        // Check if this is a valid organization slug
        Optional<OrganizationThemeDto> orgTheme = organizationService.getThemeBySlugOptional(slug);
        if (orgTheme.isEmpty()) {
            return "error/404";
        }

        Optional<User> currentUser = userService.getCurrentUser();

        if (currentUser.isPresent()) {
            UUID userId = currentUser.get().getId();
            model.addAttribute("conversations", conversationService.getConversationsForUser(userId));
            model.addAttribute("currentConversation", conversationService.getConversationForUser(id, userId).orElse(null));
            model.addAttribute("currentUser", currentUser.get());
        } else {
            model.addAttribute("conversations", conversationService.getAllConversations());
            model.addAttribute("currentConversation", conversationService.getConversation(id).orElse(null));
        }

        model.addAttribute("models", chatService.getAvailableModels());
        model.addAttribute("conversationId", id);
        model.addAttribute("orgTheme", orgTheme.get());
        model.addAttribute("orgSlug", slug);
        model.addAttribute("appDataJson", buildAppDataJson(id, currentUser.orElse(null)));
        return "index";
    }

    @GetMapping("/workspace")
    public String workspace(Model model) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty()) {
            return "redirect:/login.html";
        }
        model.addAttribute("currentUser", currentUser.get());
        return "workspace";
    }

    @GetMapping("/workspace/channels")
    public String workspaceChannels(Model model) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty()) {
            return "redirect:/login.html";
        }
        model.addAttribute("currentUser", currentUser.get());
        return "workspace/channels";
    }

    @GetMapping("/workspace/notes")
    public String workspaceNotes(Model model) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty()) {
            return "redirect:/login.html";
        }
        model.addAttribute("currentUser", currentUser.get());
        return "workspace/notes";
    }

    @GetMapping("/workspace/memory")
    public String workspaceMemory(Model model) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty()) {
            return "redirect:/login.html";
        }
        model.addAttribute("currentUser", currentUser.get());
        return "workspace/memory";
    }

    @GetMapping("/workspace/prompts")
    public String workspacePrompts(Model model) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty()) {
            return "redirect:/login.html";
        }
        model.addAttribute("currentUser", currentUser.get());
        return "workspace/prompts";
    }

    @GetMapping("/workspace/documents")
    public String workspaceDocuments(Model model) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty()) {
            return "redirect:/login.html";
        }
        model.addAttribute("currentUser", currentUser.get());
        return "workspace/documents";
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isEmpty()) {
            return "redirect:/login.html";
        }

        model.addAttribute("currentUser", currentUser.get());
        model.addAttribute("models", chatService.getAvailableModels());
        return "settings";
    }

    @GetMapping("/chat/{id}")
    public String chat(@PathVariable UUID id, Model model) {
        Optional<User> currentUser = userService.getCurrentUser();
        OrganizationThemeDto orgTheme = OrganizationThemeDto.createDefaultTheme();

        if (currentUser.isPresent()) {
            UUID userId = currentUser.get().getId();
            model.addAttribute("conversations", conversationService.getConversationsForUser(userId));
            model.addAttribute("currentConversation", conversationService.getConversationForUser(id, userId).orElse(null));
            model.addAttribute("currentUser", currentUser.get());
            orgTheme = organizationService.getThemeForUser(currentUser.get());
        } else {
            model.addAttribute("conversations", conversationService.getAllConversations());
            model.addAttribute("currentConversation", conversationService.getConversation(id).orElse(null));
        }

        model.addAttribute("models", chatService.getAvailableModels());
        model.addAttribute("conversationId", id);
        model.addAttribute("orgTheme", orgTheme);
        model.addAttribute("appDataJson", buildAppDataJson(id, currentUser.orElse(null)));
        return "index";
    }
}
