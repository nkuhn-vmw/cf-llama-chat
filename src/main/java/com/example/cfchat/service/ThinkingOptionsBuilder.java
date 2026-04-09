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
     * Build provider-specific {@link ChatOptions} for the given model and level.
     *
     * <p><b>Currently always returns {@code null}</b> — the Tanzu GenAI tile's
     * OpenAI-compat proxy rejects the {@code reasoning_effort} field with a
     * 422 Unprocessable Entity, even though Spring AI's
     * {@link OpenAiChatOptions#getReasoningEffort()} would otherwise be the
     * right knob for the gpt-oss family. Until the tile passes that field
     * through, we route all thinking-level control through
     * {@link #systemPromptSuffix(String, String)} instead. The kept
     * {@code OpenAiChatOptions} import documents the intended future path.
     */
    public ChatOptions buildOptions(String model, String level) {
        // GenAI tile proxy returns 422 on reasoning_effort; fall back to prompt-only.
        // See genai-tile-reasoning-effort-422.md in project memory.
        @SuppressWarnings("unused") Class<?> futureUse = OpenAiChatOptions.class;
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

        // Generic fallback (gpt-oss, llama, ministral, nemotron, etc.).
        return switch (norm) {
            case "none" -> "Respond directly. Do not show any reasoning steps or thinking out loud.";
            case "low" -> "Reason briefly before answering. Keep any reasoning to one or two sentences.";
            case "high" -> "Think step by step before answering. Show your reasoning clearly.";
            default -> "";
        };
    }
}
