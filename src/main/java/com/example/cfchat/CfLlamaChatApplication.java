package com.example.cfchat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.ArrayList;
import java.util.List;

@SpringBootApplication
public class CfLlamaChatApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(CfLlamaChatApplication.class);

        // Dynamically exclude OpenAI auto-configuration if no API key is set
        String openAiKey = System.getenv("OPENAI_API_KEY");
        if (openAiKey == null || openAiKey.isEmpty() || openAiKey.isBlank()) {
            System.setProperty("spring.autoconfigure.exclude",
                "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration," +
                "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration," +
                "org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration," +
                "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration," +
                "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration," +
                "org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration");
            System.out.println("INFO: No OPENAI_API_KEY set - OpenAI models disabled");
        }

        app.run(args);
    }
}
