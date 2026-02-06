package com.example.cfchat.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SkillTest {

    @Test
    void builder_defaults_areSet() {
        Skill skill = Skill.builder()
                .name("test-skill")
                .build();

        assertThat(skill.isEnabled()).isTrue();
    }

    @Test
    void builder_allFields_setCorrectly() {
        UUID id = UUID.randomUUID();

        Skill skill = Skill.builder()
                .id(id)
                .name("code_review")
                .displayName("Code Review")
                .description("Review code for issues")
                .systemPromptAugmentation("You are a code reviewer.")
                .toolIds("tool-1,tool-2")
                .enabled(false)
                .build();

        assertThat(skill.getId()).isEqualTo(id);
        assertThat(skill.getName()).isEqualTo("code_review");
        assertThat(skill.getDisplayName()).isEqualTo("Code Review");
        assertThat(skill.getDescription()).isEqualTo("Review code for issues");
        assertThat(skill.getSystemPromptAugmentation()).isEqualTo("You are a code reviewer.");
        assertThat(skill.getToolIds()).isEqualTo("tool-1,tool-2");
        assertThat(skill.isEnabled()).isFalse();
    }

    @Test
    void onCreate_setsTimestamp() {
        Skill skill = Skill.builder().name("test").build();
        skill.onCreate();
        assertThat(skill.getCreatedAt()).isNotNull();
    }
}
