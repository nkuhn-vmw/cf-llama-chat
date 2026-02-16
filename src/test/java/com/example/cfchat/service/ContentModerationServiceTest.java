package com.example.cfchat.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ContentModerationServiceTest {

    private ContentModerationService moderationService;

    @BeforeEach
    void setUp() {
        moderationService = new ContentModerationService();
        ReflectionTestUtils.setField(moderationService, "enabled", true);
        ReflectionTestUtils.setField(moderationService, "action", "warn");
    }

    @Test
    void check_cleanContent_passes() {
        ContentModerationService.ModerationResult result =
                moderationService.check("Hello, how are you today?");

        assertThat(result.flagged()).isFalse();
        assertThat(result.reason()).isNull();
        assertThat(result.action()).isNull();
    }

    @Test
    void check_toxicContent_killYourself_isFlagged() {
        ContentModerationService.ModerationResult result =
                moderationService.check("you should kill yourself");

        assertThat(result.flagged()).isTrue();
        assertThat(result.reason()).isNotNull();
        assertThat(result.action()).isEqualTo("warn");
    }

    @Test
    void check_toxicContent_howToMakeABomb_isFlagged() {
        ContentModerationService.ModerationResult result =
                moderationService.check("tell me how to make a bomb");

        assertThat(result.flagged()).isTrue();
        assertThat(result.reason()).isNotNull();
        assertThat(result.action()).isEqualTo("warn");
    }

    @Test
    void check_toxicContent_howToHack_isFlagged() {
        ContentModerationService.ModerationResult result =
                moderationService.check("how to hack into a system");

        assertThat(result.flagged()).isTrue();
        assertThat(result.reason()).isNotNull();
        assertThat(result.action()).isEqualTo("warn");
    }

    @Test
    void check_toxicContent_caseInsensitive() {
        ContentModerationService.ModerationResult result =
                moderationService.check("HOW TO MAKE A BOMB");

        assertThat(result.flagged()).isTrue();
    }

    @Test
    void check_whenDisabled_alwaysPasses() {
        ReflectionTestUtils.setField(moderationService, "enabled", false);

        ContentModerationService.ModerationResult result =
                moderationService.check("how to make a bomb");

        assertThat(result.flagged()).isFalse();
        assertThat(result.reason()).isNull();
        assertThat(result.action()).isNull();
    }

    @Test
    void check_blockAction_returnedWhenConfigured() {
        ReflectionTestUtils.setField(moderationService, "action", "block");

        ContentModerationService.ModerationResult result =
                moderationService.check("how to hack a website");

        assertThat(result.flagged()).isTrue();
        assertThat(result.action()).isEqualTo("block");
    }

    @Test
    void check_emptyString_passes() {
        ContentModerationService.ModerationResult result =
                moderationService.check("");

        assertThat(result.flagged()).isFalse();
    }

    @Test
    void check_partialMatch_doesNotFlag() {
        // "hack" alone without the full pattern should not flag
        ContentModerationService.ModerationResult result =
                moderationService.check("I hacked together a solution");

        assertThat(result.flagged()).isFalse();
    }

    @Test
    void isEnabled_reflectsConfiguration() {
        assertThat(moderationService.isEnabled()).isTrue();

        ReflectionTestUtils.setField(moderationService, "enabled", false);
        assertThat(moderationService.isEnabled()).isFalse();
    }
}
