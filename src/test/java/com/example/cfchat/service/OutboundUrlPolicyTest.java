package com.example.cfchat.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboundUrlPolicyTest {

    private OutboundUrlPolicy outboundUrlPolicy;

    @BeforeEach
    void setUp() {
        outboundUrlPolicy = new OutboundUrlPolicy();
        ReflectionTestUtils.setField(outboundUrlPolicy, "blockPrivateHosts", true);
    }

    @Test
    void validate_publicHttpsUrl_isAllowed() {
        OutboundUrlPolicy.ValidationResult result = outboundUrlPolicy.validate("https://example.com/path");

        assertThat(result.allowed()).isTrue();
        assertThat(result.reason()).isNull();
    }

    @Test
    void validate_privateIpv4_isRejected() {
        OutboundUrlPolicy.ValidationResult result = outboundUrlPolicy.validate("http://127.0.0.1/admin");

        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("Private");
    }

    @Test
    void validate_localhost_isRejected() {
        OutboundUrlPolicy.ValidationResult result = outboundUrlPolicy.validate("http://localhost:8080");

        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("Local hosts");
    }

    @Test
    void validate_nonHttpScheme_isRejected() {
        OutboundUrlPolicy.ValidationResult result = outboundUrlPolicy.validate("ftp://example.com/file.txt");

        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("http and https");
    }

    @Test
    void assertAllowed_invalidUrl_throwsHelpfulMessage() {
        assertThatThrownBy(() -> outboundUrlPolicy.assertAllowed("http://127.0.0.1", "Webhook URL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Webhook URL")
                .hasMessageContaining("not allowed");
    }
}
