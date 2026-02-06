package com.example.cfchat.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpServerTest {

    @Test
    void builder_defaults_areSet() {
        McpServer server = McpServer.builder()
                .name("test-server")
                .transportType(McpTransportType.SSE)
                .build();

        assertThat(server.isEnabled()).isTrue();
        assertThat(server.isRequiresAuth()).isFalse();
    }

    @Test
    void transportType_allValues_exist() {
        assertThat(McpTransportType.values()).containsExactly(
                McpTransportType.SSE,
                McpTransportType.STDIO,
                McpTransportType.STREAMABLE_HTTP
        );
    }

    @Test
    void onCreate_setsTimestamps() {
        McpServer server = McpServer.builder()
                .name("test")
                .transportType(McpTransportType.SSE)
                .build();
        server.onCreate();
        assertThat(server.getCreatedAt()).isNotNull();
        assertThat(server.getUpdatedAt()).isNotNull();
    }

    @Test
    void onUpdate_updatesTimestamp() {
        McpServer server = McpServer.builder()
                .name("test")
                .transportType(McpTransportType.SSE)
                .build();
        server.onUpdate();
        assertThat(server.getUpdatedAt()).isNotNull();
    }

    @Test
    void sseServer_hasUrl() {
        McpServer server = McpServer.builder()
                .name("sse-server")
                .transportType(McpTransportType.SSE)
                .url("http://localhost:8080/sse")
                .build();

        assertThat(server.getUrl()).isEqualTo("http://localhost:8080/sse");
        assertThat(server.getCommand()).isNull();
    }

    @Test
    void stdioServer_hasCommand() {
        McpServer server = McpServer.builder()
                .name("stdio-server")
                .transportType(McpTransportType.STDIO)
                .command("npx")
                .args("-y @modelcontextprotocol/server")
                .build();

        assertThat(server.getCommand()).isEqualTo("npx");
        assertThat(server.getArgs()).contains("@modelcontextprotocol/server");
    }
}
