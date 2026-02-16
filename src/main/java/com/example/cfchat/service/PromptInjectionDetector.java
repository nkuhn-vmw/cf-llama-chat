package com.example.cfchat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Service
@Slf4j
public class PromptInjectionDetector {

    @Value("${security.prompt-injection.enabled:true}")
    private boolean enabled;

    @Value("${security.prompt-injection.action:warn}")
    private String action; // warn or block

    private static final List<Pattern> PATTERNS = List.of(
        Pattern.compile("ignore (all )?(previous|above|prior) instructions", Pattern.CASE_INSENSITIVE),
        Pattern.compile("you are now", Pattern.CASE_INSENSITIVE),
        Pattern.compile("new instruction:", Pattern.CASE_INSENSITIVE),
        Pattern.compile("system:\\s*", Pattern.CASE_INSENSITIVE),
        Pattern.compile("forget (all |everything |your )(previous |prior )?instructions", Pattern.CASE_INSENSITIVE),
        Pattern.compile("disregard (all |any )?(previous |prior )?instructions", Pattern.CASE_INSENSITIVE),
        Pattern.compile("override (the |your )?system (prompt|message)", Pattern.CASE_INSENSITIVE)
    );

    public record InjectionResult(boolean detected, String pattern, String action) {}

    public boolean isEnabled() { return enabled; }

    public InjectionResult check(String message) {
        if (!enabled || message == null) return new InjectionResult(false, null, null);

        for (Pattern p : PATTERNS) {
            if (p.matcher(message).find()) {
                log.warn("Prompt injection pattern detected: {}", p.pattern());
                return new InjectionResult(true, p.pattern(), action);
            }
        }
        return new InjectionResult(false, null, null);
    }
}
