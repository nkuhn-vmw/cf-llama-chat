package com.example.cfchat.controller;

import com.example.cfchat.auth.UserService;
import com.example.cfchat.model.User;
import com.example.cfchat.service.ChatService;
import com.example.cfchat.service.ConversationService;
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

    @GetMapping("/")
    public String index(Model model) {
        Optional<User> currentUser = userService.getCurrentUser();
        if (currentUser.isPresent()) {
            UUID userId = currentUser.get().getId();
            model.addAttribute("conversations", conversationService.getConversationsForUser(userId));
            model.addAttribute("currentUser", currentUser.get());
        } else {
            model.addAttribute("conversations", conversationService.getAllConversations());
        }
        model.addAttribute("models", chatService.getAvailableModels());
        return "index";
    }

    @GetMapping("/chat/{id}")
    public String chat(@PathVariable UUID id, Model model) {
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
        return "index";
    }
}
