package com.example.cfchat.controller;

import com.example.cfchat.auth.UserService;
import com.example.cfchat.dto.OrganizationThemeDto;
import com.example.cfchat.model.User;
import com.example.cfchat.service.ChatService;
import com.example.cfchat.service.ConversationService;
import com.example.cfchat.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Optional;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class WebController {

    private final ConversationService conversationService;
    private final ChatService chatService;
    private final UserService userService;
    private final OrganizationService organizationService;

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
        return "index";
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
        return "index";
    }
}
