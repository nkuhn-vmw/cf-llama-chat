package com.example.cfchat.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!cloud")
public class SpringAiConfig {

    /**
     * Primary ChatClient using OpenAI model for local development.
     */
    @Bean
    @Primary
    @ConditionalOnBean(OpenAiChatModel.class)
    public ChatClient primaryChatClient(OpenAiChatModel openAiChatModel) {
        return ChatClient.builder(openAiChatModel).build();
    }

    /**
     * Ollama ChatClient for local development when Ollama is available.
     */
    @Bean
    @ConditionalOnBean(OllamaChatModel.class)
    public ChatClient ollamaChatClient(OllamaChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
