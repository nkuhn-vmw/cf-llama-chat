package com.example.cfchat.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ToolTest {

    @Test
    void builder_defaults_areSet() {
        Tool tool = Tool.builder()
                .name("test-tool")
                .build();

        assertThat(tool.getType()).isEqualTo(ToolType.MCP);
        assertThat(tool.isEnabled()).isTrue();
    }

    @Test
    void toolType_allValues_exist() {
        assertThat(ToolType.values()).containsExactly(ToolType.MCP, ToolType.CUSTOM);
    }

    @Test
    void builder_allFields_setCorrectly() {
        UUID id = UUID.randomUUID();
        UUID serverId = UUID.randomUUID();

        Tool tool = Tool.builder()
                .id(id)
                .name("web_search")
                .displayName("Web Search")
                .description("Search the web")
                .type(ToolType.MCP)
                .mcpServerId(serverId)
                .inputSchema("{\"type\":\"object\"}")
                .enabled(false)
                .build();

        assertThat(tool.getId()).isEqualTo(id);
        assertThat(tool.getName()).isEqualTo("web_search");
        assertThat(tool.getDisplayName()).isEqualTo("Web Search");
        assertThat(tool.getDescription()).isEqualTo("Search the web");
        assertThat(tool.getMcpServerId()).isEqualTo(serverId);
        assertThat(tool.getInputSchema()).isEqualTo("{\"type\":\"object\"}");
        assertThat(tool.isEnabled()).isFalse();
    }

    @Test
    void onCreate_setsTimestamp() {
        Tool tool = Tool.builder().name("test").build();
        tool.onCreate();
        assertThat(tool.getCreatedAt()).isNotNull();
    }
}
