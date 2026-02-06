package com.example.cfchat.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownServiceTest {

    private MarkdownService markdownService;

    @BeforeEach
    void setUp() {
        markdownService = new MarkdownService();
    }

    @Test
    void toHtml_nullInput_returnsEmptyString() {
        assertThat(markdownService.toHtml(null)).isEmpty();
    }

    @Test
    void toHtml_blankInput_returnsEmptyString() {
        assertThat(markdownService.toHtml("   ")).isEmpty();
    }

    @Test
    void toHtml_emptyInput_returnsEmptyString() {
        assertThat(markdownService.toHtml("")).isEmpty();
    }

    @Test
    void toHtml_plainText_wrapsParagraph() {
        String result = markdownService.toHtml("Hello world");
        assertThat(result).contains("<p>Hello world</p>");
    }

    @Test
    void toHtml_bold_rendersStrong() {
        String result = markdownService.toHtml("**bold text**");
        assertThat(result).contains("<strong>bold text</strong>");
    }

    @Test
    void toHtml_italic_rendersEm() {
        String result = markdownService.toHtml("*italic text*");
        assertThat(result).contains("<em>italic text</em>");
    }

    @Test
    void toHtml_heading_rendersH1() {
        String result = markdownService.toHtml("# Heading");
        assertThat(result).contains("<h1>Heading</h1>");
    }

    @Test
    void toHtml_codeBlock_rendersPreCode() {
        String result = markdownService.toHtml("```java\nSystem.out.println();\n```");
        assertThat(result).contains("<pre");
        assertThat(result).contains("<code");
        assertThat(result).contains("language-java");
    }

    @Test
    void toHtml_codeBlockWithoutLanguage_rendersPreCode() {
        String result = markdownService.toHtml("```\nsome code\n```");
        assertThat(result).contains("<pre>");
        assertThat(result).contains("<code>");
    }

    @Test
    void toHtml_inlineCode_rendersCode() {
        String result = markdownService.toHtml("Use `code` here");
        assertThat(result).contains("<code>code</code>");
    }

    @Test
    void toHtml_link_rendersAnchor() {
        String result = markdownService.toHtml("[link](https://example.com)");
        assertThat(result).contains("<a href=\"https://example.com\">");
    }

    @Test
    void toHtml_unorderedList_rendersUl() {
        String result = markdownService.toHtml("- item 1\n- item 2");
        assertThat(result).contains("<ul>");
        assertThat(result).contains("<li>item 1</li>");
        assertThat(result).contains("<li>item 2</li>");
    }

    @Test
    void toHtml_table_rendersTable() {
        String markdown = "| Col1 | Col2 |\n| --- | --- |\n| A | B |";
        String result = markdownService.toHtml(markdown);
        assertThat(result).contains("<table>");
        assertThat(result).contains("<th>Col1</th>");
        assertThat(result).contains("<td>A</td>");
    }

    @Test
    void toHtml_strikethrough_rendersDel() {
        String result = markdownService.toHtml("~~strikethrough~~");
        assertThat(result).contains("<del>strikethrough</del>");
    }

    @Test
    void toHtml_autolink_rendersLink() {
        String result = markdownService.toHtml("Visit https://example.com today");
        assertThat(result).contains("<a href=\"https://example.com\">");
    }

    @Test
    void toHtml_taskList_rendersCheckbox() {
        String result = markdownService.toHtml("- [ ] unchecked\n- [x] checked");
        assertThat(result).contains("type=\"checkbox\"");
    }

    @Test
    void toHtml_blockquote_rendersBlockquote() {
        String result = markdownService.toHtml("> quoted text");
        assertThat(result).contains("<blockquote>");
    }

    @Test
    void toHtml_multipleCodeBlocks_rendersAll() {
        String markdown = "```python\nprint('hello')\n```\n\nText\n\n```javascript\nconsole.log('hi');\n```";
        String result = markdownService.toHtml(markdown);
        assertThat(result).contains("language-python");
        assertThat(result).contains("language-javascript");
    }
}
