package com.example.cfchat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.ArrayList;
import java.util.List;

@SpringBootApplication
@EnableAsync
public class CfLlamaChatApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(CfLlamaChatApplication.class);

        // Always exclude PgVectorStore auto-configuration since we define our own
        List<String> excludes = new ArrayList<>();
        excludes.add("org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration");

        // Dynamically exclude OpenAI auto-configuration if no API key is set
        String openAiKey = System.getenv("OPENAI_API_KEY");
        if (openAiKey == null || openAiKey.isEmpty() || openAiKey.isBlank()) {
            excludes.add("org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration");
            excludes.add("org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration");
            excludes.add("org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration");
            excludes.add("org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration");
            excludes.add("org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration");
            excludes.add("org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration");
            System.out.println("INFO: No OPENAI_API_KEY set - OpenAI models disabled");
        }

        System.setProperty("spring.autoconfigure.exclude", String.join(",", excludes));

        app.run(args);
    }
}
