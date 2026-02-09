package com.example.cfchat.e2e;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "e2e"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BasePlaywrightIT {

    @LocalServerPort
    protected int port;

    protected Playwright playwright;
    protected Browser browser;
    protected BrowserContext context;
    protected Page page;

    @BeforeAll
    void setUpBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    void tearDownBrowser() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @BeforeEach
    void setUpContext() {
        context = browser.newContext();
        page = context.newPage();
    }

    @AfterEach
    void tearDownContext() {
        if (context != null) context.close();
    }

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    protected void loginAsAdmin() {
        page.navigate(baseUrl() + "/login.html");
        page.fill("#username", "admin");
        page.fill("#password", "TestPass123");
        page.click("button[type='submit']");
        page.waitForSelector("#chatForm", new Page.WaitForSelectorOptions()
                .setTimeout(10_000));
    }
}
