package com.example.cfchat.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.McpToolsChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

@Component
public class McpClientFactory {

    private static final Logger logger = LoggerFactory.getLogger(McpClientFactory.class);
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(10);

    private final SSLContext sslContext;
    private final ApplicationEventPublisher eventPublisher;

    public McpClientFactory(SSLContext sslContext, ApplicationEventPublisher eventPublisher) {
        this.sslContext = sslContext;
        this.eventPublisher = eventPublisher;
    }

    public McpSyncClient createSseClient(String serverUrl, Duration connectTimeout, Duration requestTimeout) {
        return createSseClient(serverUrl, connectTimeout, requestTimeout, Map.of());
    }

    public McpSyncClient createSseClient(String serverUrl, Duration connectTimeout, Duration requestTimeout, Map<String, String> headers) {
        HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                .sslContext(sslContext)
                .connectTimeout(connectTimeout);

        HttpClientSseClientTransport.Builder transportBuilder = HttpClientSseClientTransport.builder(serverUrl)
                .clientBuilder(clientBuilder);

        // Use httpRequestCustomizer for per-request header injection (applied on every request)
        if (!headers.isEmpty()) {
            logger.info("Creating SSE client for {} with {} custom header(s): {}", serverUrl, headers.size(), headers.keySet());
            transportBuilder.httpRequestCustomizer((builder, method, endpoint, body, context) -> {
                headers.forEach(builder::header);
            });
        }

        HttpClientSseClientTransport transport = transportBuilder.build();

        return McpClient.sync(transport)
                .requestTimeout(requestTimeout)
                .toolsChangeConsumer(tools -> {
                    logger.info("MCP server {} tools changed, publishing event (new tool count: {})",
                            serverUrl, tools.size());
                    eventPublisher.publishEvent(new McpToolsChangedEvent(serverUrl, tools));
                })
                .build();
    }

    public McpSyncClient createStreamableClient(String serverUrl, Duration connectTimeout, Duration requestTimeout) {
        return createStreamableClient(serverUrl, connectTimeout, requestTimeout, Map.of());
    }

    public McpSyncClient createStreamableClient(String serverUrl, Duration connectTimeout, Duration requestTimeout, Map<String, String> headers) {
        HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                .sslContext(sslContext)
                .connectTimeout(connectTimeout);

        HttpClientStreamableHttpTransport.Builder transportBuilder = HttpClientStreamableHttpTransport.builder(serverUrl)
                .clientBuilder(clientBuilder)
                .resumableStreams(true);

        // Use httpRequestCustomizer for per-request header injection (applied on every request)
        if (!headers.isEmpty()) {
            logger.info("Creating Streamable HTTP client for {} with {} custom header(s): {}", serverUrl, headers.size(), headers.keySet());
            transportBuilder.httpRequestCustomizer((builder, method, endpoint, body, context) -> {
                headers.forEach(builder::header);
            });
        }

        HttpClientStreamableHttpTransport transport = transportBuilder.build();

        return McpClient.sync(transport)
                .requestTimeout(requestTimeout)
                .toolsChangeConsumer(tools -> {
                    logger.info("MCP server {} tools changed, publishing event (new tool count: {})",
                            serverUrl, tools.size());
                    eventPublisher.publishEvent(new McpToolsChangedEvent(serverUrl, tools));
                })
                .build();
    }

    public McpSyncClient createHealthCheckClient(String serverUrl, ProtocolType protocol, Map<String, String> headers) {
        return switch (protocol) {
            case ProtocolType.StreamableHttp streamableHttp ->
                    createStreamableClient(serverUrl, HEALTH_CHECK_TIMEOUT, HEALTH_CHECK_TIMEOUT, headers);
            case ProtocolType.SSE sse ->
                    createSseClient(serverUrl, HEALTH_CHECK_TIMEOUT, HEALTH_CHECK_TIMEOUT, headers);
            case ProtocolType.Legacy legacy ->
                    createSseClient(serverUrl, HEALTH_CHECK_TIMEOUT, HEALTH_CHECK_TIMEOUT, headers);
        };
    }
}
