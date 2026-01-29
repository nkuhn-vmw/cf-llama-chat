package com.example.cfchat.controller;

import com.example.cfchat.service.ChatService;
import com.example.cfchat.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class WebController {

    private final ConversationService conversationService;
    private final ChatService chatService;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("conversations", conversationService.getAllConversations());
        model.addAttribute("models", chatService.getAvailableModels());
        return "index";
    }

    @GetMapping("/chat/{id}")
    public String chat(@PathVariable UUID id, Model model) {
        model.addAttribute("conversations", conversationService.getAllConversations());
        model.addAttribute("models", chatService.getAvailableModels());
        model.addAttribute("currentConversation", conversationService.getConversation(id).orElse(null));
        model.addAttribute("conversationId", id);
        return "index";
    }
}
