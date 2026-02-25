package com.example.cfchat.mcp;

import io.pivotal.cfenv.boot.genai.GenaiLocator;
import io.pivotal.cfenv.core.CfCredentials;
import io.pivotal.cfenv.core.CfEnv;
import io.pivotal.cfenv.core.CfService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class McpDiscoveryService {
    private static final Logger logger = LoggerFactory.getLogger(McpDiscoveryService.class);

    public static final String MCP_SERVICE_URL = "mcpServiceURL";
    public static final String MCP_SSE_URL = "mcpSseURL";
    public static final String MCP_STREAMABLE_URL = "mcpStreamableURL";
    public static final String TAG_MCP_SSE = "mcpSseURL";
    public static final String TAG_MCP_STREAMABLE = "mcpStreamableURL";
    public static final String CREDENTIALS_URI_KEY = "uri";

    private final CfEnv cfEnv;
    private final GenaiLocator genaiLocator;

    public McpDiscoveryService(@Nullable GenaiLocator genaiLocator) {
        this.cfEnv = new CfEnv();
        this.genaiLocator = genaiLocator;

        if (genaiLocator != null) {
            logger.debug("GenaiLocator bean detected - will check for dynamic MCP service discovery");
        } else {
            logger.debug("No GenaiLocator bean available - using CF service-based MCP discovery only");
        }
    }

    public List<String> getMcpServiceUrlsFromLocator() {
        if (genaiLocator == null) {
            return List.of();
        }
        try {
            return genaiLocator.getMcpServers().stream()
                    .map(GenaiLocator.McpConnectivity::url)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.debug("Error getting MCP service URLs from GenaiLocator: {}", e.getMessage());
            return List.of();
        }
    }

    public List<String> getMcpServiceUrls() {
        try {
            return cfEnv.findAllServices().stream()
                    .filter(this::hasMcpServiceUrl)
                    .map(service -> service.getCredentials().getString(MCP_SERVICE_URL))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.warn("Error getting MCP service URLs: {}", e.getMessage());
            return List.of();
        }
    }

    public List<String> getAllMcpServiceUrls() {
        List<String> locatorUrls = getMcpServiceUrlsFromLocator();
        List<String> cfUrls = getMcpServiceUrls();

        if (!locatorUrls.isEmpty()) {
            logger.debug("Using MCP service URLs from GenaiLocator: {}", locatorUrls);
            return locatorUrls;
        } else if (!cfUrls.isEmpty()) {
            logger.debug("Using MCP service URLs from CF services: {}", cfUrls);
            return cfUrls;
        } else {
            logger.debug("No MCP service URLs found from any source");
            return List.of();
        }
    }

    public boolean hasMcpServiceUrl(CfService service) {
        CfCredentials credentials = service.getCredentials();
        return credentials != null && credentials.getString(MCP_SERVICE_URL) != null;
    }

    public List<McpServiceConfiguration> getMcpServicesWithProtocol() {
        try {
            return cfEnv.findAllServices().stream()
                    .map(this::extractMcpServiceConfiguration)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.warn("Error getting MCP services with protocol information: {}", e.getMessage());
            return List.of();
        }
    }

    private McpServiceConfiguration extractMcpServiceConfiguration(CfService service) {
        McpServiceConfiguration tagBasedConfig = extractFromTags(service);
        if (tagBasedConfig != null) {
            return tagBasedConfig;
        }
        McpServiceConfiguration credentialConfig = extractFromCredentials(service);
        if (credentialConfig != null) {
            return credentialConfig;
        }
        return extractFromLabel(service);
    }

    private McpServiceConfiguration extractFromTags(CfService service) {
        CfCredentials credentials = service.getCredentials();
        if (credentials == null) {
            return null;
        }

        Map<String, String> headers = extractHeaders(credentials);

        if (service.existsByTagIgnoreCase(TAG_MCP_STREAMABLE)) {
            String uri = credentials.getString(CREDENTIALS_URI_KEY);
            if (isValidUrl(uri)) {
                logger.debug("Found MCP Streamable service '{}' via tag with URI: {}",
                    service.getName(), uri);
                return new McpServiceConfiguration(
                    service.getName(),
                    uri,
                    new ProtocolType.StreamableHttp(),
                    headers
                );
            }
        }

        if (service.existsByTagIgnoreCase(TAG_MCP_SSE)) {
            String uri = credentials.getString(CREDENTIALS_URI_KEY);
            if (isValidUrl(uri)) {
                logger.debug("Found MCP SSE service '{}' via tag with URI: {}",
                    service.getName(), uri);
                return new McpServiceConfiguration(
                    service.getName(),
                    uri,
                    new ProtocolType.SSE(),
                    headers
                );
            }
        }

        return null;
    }

    private McpServiceConfiguration extractFromCredentials(CfService service) {
        CfCredentials credentials = service.getCredentials();
        if (credentials == null) {
            return null;
        }

        Map<String, Object> credentialsMap = credentials.getMap();
        Map<String, String> headers = extractHeaders(credentials);

        if (credentialsMap.containsKey(MCP_STREAMABLE_URL)) {
            String url = (String) credentialsMap.get(MCP_STREAMABLE_URL);
            if (isValidUrl(url)) {
                logger.debug("Found legacy MCP Streamable service '{}' via credentials", service.getName());
                return new McpServiceConfiguration(service.getName(), url, new ProtocolType.StreamableHttp(), headers);
            }
        }

        if (credentialsMap.containsKey(MCP_SSE_URL)) {
            String url = (String) credentialsMap.get(MCP_SSE_URL);
            if (isValidUrl(url)) {
                logger.debug("Found legacy MCP SSE service '{}' via credentials", service.getName());
                return new McpServiceConfiguration(service.getName(), url, new ProtocolType.SSE(), headers);
            }
        }

        if (credentialsMap.containsKey(MCP_SERVICE_URL)) {
            String url = (String) credentialsMap.get(MCP_SERVICE_URL);
            if (isValidUrl(url)) {
                logger.debug("Found legacy MCP service '{}' via credentials with mcpServiceURL", service.getName());
                return new McpServiceConfiguration(service.getName(), url, new ProtocolType.Legacy(), headers);
            }
        }

        return null;
    }

    private McpServiceConfiguration extractFromLabel(CfService service) {
        String label = service.getLabel();
        if (label == null || !label.toLowerCase().contains("mcp")) {
            return null;
        }

        CfCredentials credentials = service.getCredentials();
        if (credentials == null) {
            return null;
        }

        String uri = credentials.getString(CREDENTIALS_URI_KEY);
        if (!isValidUrl(uri)) {
            return null;
        }

        Map<String, String> headers = extractHeaders(credentials);
        logger.info("Found MCP service '{}' via label '{}' with URI: {}, headers: {}",
                service.getName(), label, uri, headers.keySet());
        return new McpServiceConfiguration(
                service.getName(),
                uri,
                new ProtocolType.StreamableHttp(),
                headers
        );
    }

    private boolean isValidUrl(String url) {
        return url != null && !url.trim().isEmpty();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> extractHeaders(CfCredentials credentials) {
        if (credentials == null) {
            return Map.of();
        }

        Map<String, Object> credentialsMap = credentials.getMap();
        if (credentialsMap == null || !credentialsMap.containsKey("headers")) {
            return Map.of();
        }

        Object headersObj = credentialsMap.get("headers");
        if (!(headersObj instanceof Map)) {
            return Map.of();
        }

        try {
            Map<String, Object> headersMap = (Map<String, Object>) headersObj;
            java.util.Map<String, String> result = new java.util.HashMap<>();

            for (Map.Entry<String, Object> entry : headersMap.entrySet()) {
                if (entry.getValue() != null) {
                    result.put(entry.getKey(), entry.getValue().toString());
                }
            }

            return Map.copyOf(result);
        } catch (Exception e) {
            logger.warn("Error extracting headers from credentials: {}", e.getMessage());
            return Map.of();
        }
    }

    public record McpServiceConfiguration(
            String serviceName,
            String serverUrl,
            ProtocolType protocol,
            Map<String, String> headers
    ) {
        public McpServiceConfiguration(String serviceName, String serverUrl, ProtocolType protocol) {
            this(serviceName, serverUrl, protocol, Map.of());
        }
    }
}
