package com.example.cfchat.e2e;

import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

class ChatSmokeIT extends BasePlaywrightIT {

    @Test
    void loginPageRendersAndAcceptsCredentials() {
        page.navigate(baseUrl() + "/login.html");

        assertThat(page.locator("#username")).isVisible();
        assertThat(page.locator("#password")).isVisible();
        assertThat(page.locator("button[type='submit']")).isVisible();

        // Login and verify redirect to chat
        page.fill("#username", "admin");
        page.fill("#password", "TestPass123");
        page.click("button[type='submit']");
        page.waitForSelector("#chatForm", new com.microsoft.playwright.Page.WaitForSelectorOptions()
                .setTimeout(10_000));
        assertThat(page.locator("#chatForm")).isVisible();
    }

    @Test
    void chatPageLoadsAfterLogin() {
        loginAsAdmin();

        assertThat(page.locator("#messagesContainer")).isVisible();
        assertThat(page.locator("#messageInput")).isVisible();
        assertThat(page.locator("#sendBtn")).isVisible();
        assertThat(page.locator("#modelSelect")).isVisible();
        assertThat(page.locator("#sidebar")).isVisible();
        assertThat(page.locator("#welcomeScreen")).isVisible();
    }

    @Test
    void chatInputAcceptsText() {
        loginAsAdmin();

        page.fill("#messageInput", "Hello, world!");
        assertThat(page.locator("#messageInput")).hasValue("Hello, world!");
    }

    @Test
    void toggleStatesChange() {
        loginAsAdmin();

        // Stream toggle
        var streamToggle = page.locator("#streamToggle");
        assertThat(streamToggle).isChecked();
        streamToggle.uncheck();
        assertThat(streamToggle).not().isChecked();
        streamToggle.check();
        assertThat(streamToggle).isChecked();

        // Tools toggle exists in DOM (may be hidden if no tools configured)
        assertThat(page.locator("#useToolsToggle")).hasCount(1);
    }

    @Test
    void unauthenticatedAccessRedirectsToLogin() {
        page.navigate(baseUrl() + "/");
        page.waitForURL("**/login.html**");
        assertThat(page.locator("#username")).isVisible();
    }
}
