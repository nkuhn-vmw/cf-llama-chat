package com.example.cfchat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.IDN;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;

@Service
@Slf4j
public class OutboundUrlPolicy {

    @Value("${security.outbound.block-private-hosts:true}")
    private boolean blockPrivateHosts;

    public record ValidationResult(boolean allowed, String reason) {}

    public ValidationResult validate(String url) {
        if (url == null || url.isBlank()) {
            return new ValidationResult(false, "URL is required");
        }

        final URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            return new ValidationResult(false, "URL is malformed");
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            return new ValidationResult(false, "Only http and https URLs are allowed");
        }

        if (uri.getUserInfo() != null) {
            return new ValidationResult(false, "User info in URLs is not allowed");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return new ValidationResult(false, "URL host is required");
        }

        String normalizedHost = IDN.toASCII(host, IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.ROOT);
        if ("localhost".equals(normalizedHost) || normalizedHost.endsWith(".localhost") || normalizedHost.endsWith(".local")) {
            return new ValidationResult(false, "Local hosts are not allowed");
        }

        if (!blockPrivateHosts) {
            return new ValidationResult(true, null);
        }

        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (isBlockedAddress(address)) {
                    return new ValidationResult(false, "Private or special-use network addresses are not allowed");
                }
            }
        } catch (Exception e) {
            log.debug("Failed to resolve outbound URL host {}: {}", host, e.getMessage());
            return new ValidationResult(false, "URL host could not be resolved");
        }

        return new ValidationResult(true, null);
    }

    public boolean isAllowed(String url) {
        return validate(url).allowed();
    }

    public void assertAllowed(String url, String purpose) {
        ValidationResult result = validate(url);
        if (!result.allowed()) {
            throw new IllegalArgumentException(purpose + " is not allowed: " + result.reason());
        }
    }

    private boolean isBlockedAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        if (address instanceof Inet6Address inet6) {
            byte[] bytes = inet6.getAddress();
            return bytes.length > 1 && (bytes[0] & (byte) 0xfe) == (byte) 0xfc;
        }

        byte[] bytes = address.getAddress();
        if (bytes.length != 4) {
            return false;
        }

        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);

        if (first == 0 || first == 10 || first == 127) {
            return true;
        }
        if (first == 100 && second >= 64 && second <= 127) {
            return true;
        }
        if (first == 169 && second == 254) {
            return true;
        }
        if (first == 172 && second >= 16 && second <= 31) {
            return true;
        }
        if (first == 192 && second == 168) {
            return true;
        }
        if (first == 198 && (second == 18 || second == 19)) {
            return true;
        }
        return first >= 224;
    }
}
