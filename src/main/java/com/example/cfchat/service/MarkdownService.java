package com.example.cfchat.service;

import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.AttributeProvider;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class MarkdownService {

    private final Parser parser;
    private final HtmlRenderer renderer;

    public MarkdownService() {
        // Configure extensions for GitHub Flavored Markdown
        List<Extension> extensions = Arrays.asList(
                TablesExtension.create(),
                StrikethroughExtension.create(),
                AutolinkExtension.create(),
                TaskListItemsExtension.create()
        );

        this.parser = Parser.builder()
                .extensions(extensions)
                .build();

        this.renderer = HtmlRenderer.builder()
                .extensions(extensions)
                .softbreak("<br/>")
                .attributeProviderFactory(context -> new CodeBlockAttributeProvider())
                .build();
    }

    public String toHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        Node document = parser.parse(markdown);
        return renderer.render(document);
    }

    /**
     * Attribute provider to add language class to code blocks for syntax highlighting
     */
    private static class CodeBlockAttributeProvider implements AttributeProvider {
        @Override
        public void setAttributes(Node node, String tagName, Map<String, String> attributes) {
            if (node instanceof FencedCodeBlock fencedCodeBlock) {
                String language = fencedCodeBlock.getInfo();
                if (language != null && !language.isEmpty()) {
                    // Add language class for highlight.js
                    String existingClass = attributes.getOrDefault("class", "");
                    String newClass = existingClass.isEmpty()
                            ? "language-" + language
                            : existingClass + " language-" + language;
                    attributes.put("class", newClass);
                }
            }
        }
    }
}
