package com.example.cfchat.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class McpConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(McpConfiguration.class);

    @Bean
    public SSLContext sslContext() throws NoSuchAlgorithmException {
        return SSLContext.getDefault();
    }

    @Bean
    public List<McpServerService> mcpServerServices(
            McpDiscoveryService discoveryService,
            McpClientFactory clientFactory) {

        List<McpDiscoveryService.McpServiceConfiguration> configs = discoveryService.getMcpServicesWithProtocol();

        if (configs.isEmpty()) {
            logger.info("No MCP services found in Cloud Foundry bindings");
            return List.of();
        }

        List<McpServerService> services = configs.stream()
                .map(config -> {
                    logger.info("Configuring MCP server: {} ({}) at {}",
                            config.serviceName(), config.protocol().displayName(), config.serverUrl());
                    return new McpServerService(
                            config.serviceName(),
                            config.serverUrl(),
                            config.protocol(),
                            config.headers(),
                            clientFactory
                    );
                })
                .collect(Collectors.toList());

        logger.info("Configured {} MCP server service(s)", services.size());
        return services;
    }
}
