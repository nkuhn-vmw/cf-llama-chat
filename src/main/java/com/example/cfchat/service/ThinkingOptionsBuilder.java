package com.example.cfchat.service;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Translates a frontend "thinking level" into provider-specific chat options
 * and/or system-prompt prefixes.
 *
 * <p>The Tanzu GenAI tile proxies several runtimes (Ollama, vLLM, SaaS) under
 * one OpenAI-compatible facade, but the runtimes don't expose reasoning
 * controls the same way:
 *
 * <ul>
 *   <li><b>gpt-oss family</b> (reasoning model served by vLLM): supports
 *       {@code reasoning.effort: minimal|low|medium|high} via Spring AI's
 *       {@link OpenAiChatOptions#getReasoningEffort()}.</li>
 *   <li><b>Qwen3 family</b> (Ollama or vLLM): respects a {@code /no_think}
 *       system directive to skip reasoning entirely. Does not have native
 *       low/medium/high levels — anything above {@code none} just enables
 *       default thinking.</li>
 *   <li><b>Other Ollama models</b> (llama3.1, ministral, nemotron, etc.):
 *       no native control. We append a system-prompt nudge.</li>
 * </ul>
 *
 * <p>The {@code none} level is the most user-impactful: per-instance, modern
 * Qwen3 models burn most of their CPU on reasoning tokens, so {@code /no_think}
 * cuts TTFT dramatically.
 */
@Component
public class ThinkingOptionsBuilder {

    public static final String DEFAULT_LEVEL = "medium";
    public static final Set<String> VALID_LEVELS = Set.of("none", "low", "medium", "high");

    /** Normalize and default to {@link #DEFAULT_LEVEL}. */
    public String normalize(String level) {
        if (level == null || level.isBlank()) return DEFAULT_LEVEL;
        String lower = level.toLowerCase().trim();
        return VALID_LEVELS.contains(lower) ? lower : DEFAULT_LEVEL;
    }

    /**
     * Build provider-specific {@link ChatOptions} for the given model and level,
     * or {@code null} if no native option applies (caller should fall back to a
     * system-prompt nudge from {@link #systemPromptSuffix(String, String)}).
     */
    public ChatOptions buildOptions(String model, String level) {
        if (model == null) return null;
        String norm = normalize(level);
        String m = model.toLowerCase();

        // gpt-oss is a reasoning model — use the OpenAI-compat reasoning_effort field.
        if (m.contains("gpt-oss")) {
            String effort = switch (norm) {
                case "none" -> "minimal";
                case "low" -> "low";
                case "high" -> "high";
                default -> "medium";
            };
            return OpenAiChatOptions.builder()
                    .model(model)
                    .reasoningEffort(effort)
                    .build();
        }
        // Other models don't have a native level we can pass via OpenAiChatOptions;
        // the caller will use systemPromptSuffix() instead.
        return null;
    }

    /**
     * System-prompt suffix that nudges the model toward the requested thinking
     * depth. Used for Qwen3 ({@code /no_think} directive) and for plain models
     * without a native reasoning control. Returns empty string for the default
     * level so we don't bloat the prompt unnecessarily.
     */
    public String systemPromptSuffix(String model, String level) {
        if (model == null) return "";
        String norm = normalize(level);
        String m = model.toLowerCase();

        // Qwen3 family responds to /no_think and /think directives.
        if (m.startsWith("qwen3") || m.contains("/qwen3")) {
            if ("none".equals(norm)) return "/no_think";
            // For low/medium/high, leave default reasoning on. Qwen3 has no levels.
            return "";
        }

        // gpt-oss is handled by buildOptions; no system suffix needed.
        if (m.contains("gpt-oss")) return "";

        // Generic fallback for plain Ollama models.
        return switch (norm) {
            case "none" -> "Respond directly. Do not show any reasoning steps or thinking out loud.";
            case "low" -> "Reason briefly before answering. Keep any reasoning to one or two sentences.";
            case "high" -> "Think step by step before answering. Show your reasoning clearly.";
            default -> "";
        };
    }
}
