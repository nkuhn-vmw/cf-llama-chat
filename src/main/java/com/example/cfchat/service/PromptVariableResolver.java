package com.example.cfchat.service;

import com.example.cfchat.model.User;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

@Service
public class PromptVariableResolver {

    public String resolve(String template, User user) {
        if (template == null) return null;

        String result = template
                .replace("{{CURRENT_DATE}}", LocalDate.now().toString())
                .replace("{{CURRENT_TIME}}", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))
                .replace("{{CURRENT_DATETIME}}", LocalDateTime.now().toString())
                .replace("{{CURRENT_WEEKDAY}}", LocalDate.now().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                .replace("{{CURRENT_MONTH}}", LocalDate.now().getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                .replace("{{CURRENT_YEAR}}", String.valueOf(LocalDate.now().getYear()));

        if (user != null) {
            result = result
                    .replace("{{USER_NAME}}", user.getDisplayName() != null ? user.getDisplayName() : user.getUsername())
                    .replace("{{USER_EMAIL}}", user.getEmail() != null ? user.getEmail() : "")
                    .replace("{{USER_ROLE}}", user.getRole() != null ? user.getRole().name() : "USER");
        }

        return result;
    }

    public String resolve(String template) {
        return resolve(template, null);
    }
}
