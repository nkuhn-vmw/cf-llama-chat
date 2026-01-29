package com.example.cfchat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.chat")
@Data
public class ChatConfig {
    private String defaultProvider = "openai";
    private String systemPrompt = "You are a helpful AI assistant.";
}
