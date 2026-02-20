package com.example.cfchat.service;

import com.example.cfchat.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class PromptVariableResolverTest {

    private PromptVariableResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new PromptVariableResolver();
    }

    @Test
    void resolve_currentDate_isReplaced() {
        String result = resolver.resolve("Today is {{CURRENT_DATE}}");

        assertThat(result).contains(LocalDate.now().toString());
        assertThat(result).doesNotContain("{{CURRENT_DATE}}");
    }

    @Test
    void resolve_currentTime_isReplaced() {
        String result = resolver.resolve("The time is {{CURRENT_TIME}}");

        assertThat(result).doesNotContain("{{CURRENT_TIME}}");
        // Verify it matches HH:mm format
        String timeStr = result.replace("The time is ", "");
        assertThat(timeStr).matches("\\d{2}:\\d{2}");
    }

    @Test
    void resolve_currentDatetime_isReplaced() {
        String result = resolver.resolve("Now: {{CURRENT_DATETIME}}");

        assertThat(result).doesNotContain("{{CURRENT_DATETIME}}");
        assertThat(result).startsWith("Now: ");
    }

    @Test
    void resolve_currentWeekday_isReplaced() {
        String result = resolver.resolve("Day: {{CURRENT_WEEKDAY}}");

        String expectedDay = LocalDate.now().getDayOfWeek()
                .getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        assertThat(result).isEqualTo("Day: " + expectedDay);
    }

    @Test
    void resolve_currentMonth_isReplaced() {
        String result = resolver.resolve("Month: {{CURRENT_MONTH}}");

        String expectedMonth = LocalDate.now().getMonth()
                .getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        assertThat(result).isEqualTo("Month: " + expectedMonth);
    }

    @Test
    void resolve_currentYear_isReplaced() {
        String result = resolver.resolve("Year: {{CURRENT_YEAR}}");

        assertThat(result).isEqualTo("Year: " + LocalDate.now().getYear());
    }

    @Test
    void resolve_userName_withDisplayName() {
        User user = User.builder()
                .username("jdoe")
                .displayName("John Doe")
                .build();

        String result = resolver.resolve("Hello {{USER_NAME}}", user);

        assertThat(result).isEqualTo("Hello John Doe");
    }

    @Test
    void resolve_userName_fallsBackToUsername() {
        User user = User.builder()
                .username("jdoe")
                .displayName(null)
                .build();

        String result = resolver.resolve("Hello {{USER_NAME}}", user);

        assertThat(result).isEqualTo("Hello jdoe");
    }

    @Test
    void resolve_userEmail_isReplaced() {
        User user = User.builder()
                .username("jdoe")
                .email("jdoe@example.com")
                .build();

        String result = resolver.resolve("Email: {{USER_EMAIL}}", user);

        assertThat(result).isEqualTo("Email: jdoe@example.com");
    }

    @Test
    void resolve_userEmail_nullEmail_replacedWithEmpty() {
        User user = User.builder()
                .username("jdoe")
                .email(null)
                .build();

        String result = resolver.resolve("Email: {{USER_EMAIL}}", user);

        assertThat(result).isEqualTo("Email: ");
    }

    @Test
    void resolve_userRole_isReplaced() {
        User user = User.builder()
                .username("admin")
                .role(User.UserRole.ADMIN)
                .build();

        String result = resolver.resolve("Role: {{USER_ROLE}}", user);

        assertThat(result).isEqualTo("Role: ADMIN");
    }

    @Test
    void resolve_userRole_nullRole_defaultsToUser() {
        User user = User.builder()
                .username("jdoe")
                .role(null)
                .build();

        String result = resolver.resolve("Role: {{USER_ROLE}}", user);

        assertThat(result).isEqualTo("Role: USER");
    }

    @Test
    void resolve_unknownVariable_passesThrough() {
        String result = resolver.resolve("Value: {{UNKNOWN_VAR}}");

        assertThat(result).isEqualTo("Value: {{UNKNOWN_VAR}}");
    }

    @Test
    void resolve_nullTemplate_returnsNull() {
        String result = resolver.resolve(null);

        assertThat(result).isNull();
    }

    @Test
    void resolve_noVariables_returnsOriginal() {
        String template = "Just a regular string with no variables";
        String result = resolver.resolve(template);

        assertThat(result).isEqualTo(template);
    }

    @Test
    void resolve_multipleVariables_allReplaced() {
        User user = User.builder()
                .username("jdoe")
                .displayName("John")
                .role(User.UserRole.USER)
                .build();

        String result = resolver.resolve(
                "Hello {{USER_NAME}}, today is {{CURRENT_DATE}} and your role is {{USER_ROLE}}", user);

        assertThat(result).doesNotContain("{{USER_NAME}}");
        assertThat(result).doesNotContain("{{CURRENT_DATE}}");
        assertThat(result).doesNotContain("{{USER_ROLE}}");
        assertThat(result).contains("John");
        assertThat(result).contains(LocalDate.now().toString());
        assertThat(result).contains("USER");
    }

    @Test
    void resolve_withoutUser_userVariablesNotReplaced() {
        String result = resolver.resolve("Hello {{USER_NAME}}");

        // Without user, user-specific variables remain unreplaced
        assertThat(result).contains("{{USER_NAME}}");
    }

    @Test
    void resolve_overloadWithoutUser_dateVariablesStillWork() {
        String result = resolver.resolve("Date: {{CURRENT_DATE}}");

        assertThat(result).doesNotContain("{{CURRENT_DATE}}");
        assertThat(result).contains(LocalDate.now().toString());
    }
}
