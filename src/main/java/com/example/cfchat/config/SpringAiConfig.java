package com.example.cfchat.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class SpringAiConfig {

    @Bean
    @Primary
    public ChatClient openAiChatClient(@Autowired(required = false) OpenAiChatModel chatModel) {
        if (chatModel != null) {
            return ChatClient.builder(chatModel).build();
        }
        return null;
    }

    @Bean
    public ChatClient ollamaChatClient(@Autowired(required = false) OllamaChatModel chatModel) {
        if (chatModel != null) {
            return ChatClient.builder(chatModel).build();
        }
        return null;
    }
}
