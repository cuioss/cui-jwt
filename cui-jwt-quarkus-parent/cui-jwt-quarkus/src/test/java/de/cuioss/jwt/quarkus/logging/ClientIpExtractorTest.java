/*
 * Copyright © 2025 CUI-OpenSource-Software (info@cuioss.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.cuioss.jwt.quarkus.logging;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for ClientIpExtractor utility class.
 * Covers various proxy header scenarios, CDN configurations, and edge cases.
 */
class ClientIpExtractorTest {

    @Test
    @DisplayName("Should return unknown when no headers provided")
    void shouldReturnUnknownWhenNoHeaders() {
        Map<String, String> headers = new HashMap<>();

        String result = ClientIpExtractor.extractClientIp(headers);

        assertEquals("unknown", result);
    }

    @Test
    @DisplayName("Should extract IP from X-Forwarded-For header")
    void shouldExtractFromXForwardedFor() {
        Map<String, String> headers = Map.of("X-Forwarded-For", "192.168.1.100");

        String result = ClientIpExtractor.extractClientIp(headers);

        assertEquals("192.168.1.100", result);
    }

    @Test
    @DisplayName("Should extract first IP from comma-separated X-Forwarded-For")
    void shouldExtractFirstFromCommaSeparatedXForwardedFor() {
        Map<String, String> headers = Map.of("X-Forwarded-For", "203.0.113.1,198.51.100.101,198.51.100.102");

        String result = ClientIpExtractor.extractClientIp(headers);

        assertEquals("203.0.113.1", result);
    }

    @Test
    @DisplayName("Should extract IP from Cloudflare CF-Connecting-IP header")
    void shouldExtractFromCloudflareHeader() {
        Map<String, String> headers = Map.of("CF-Connecting-IP", "203.0.113.50");

        String result = ClientIpExtractor.extractClientIp(headers);

        assertEquals("203.0.113.50", result);
    }

    @Test
    @DisplayName("Should prioritize CF-Connecting-IP over X-Real-IP")
    void shouldPrioritizeCloudflareOverXRealIP() {
        Map<String, String> headers = Map.of(
                "CF-Connecting-IP", "203.0.113.50",
                "X-Real-IP", "10.0.0.1"
        );

        String result = ClientIpExtractor.extractClientIp(headers);

        assertEquals("203.0.113.50", result);
    }

    @Test
    @DisplayName("Should extract IP from True-Client-IP header")
    void shouldExtractFromTrueClientIP() {
        Map<String, String> headers = Map.of("True-Client-IP", "198.51.100.200");

        String result = ClientIpExtractor.extractClientIp(headers);

        assertEquals("198.51.100.200", result);
    }

    @Test
    @DisplayName("Should extract IP from X-Real-IP header")
    void shouldExtractFromXRealIP() {
        Map<String, String> headers = Map.of("X-Real-IP", "10.0.0.100");

        String result = ClientIpExtractor.extractClientIp(headers);

        assertEquals("10.0.0.100", result);
    }

    @Test
    @DisplayName("Should extract IP from Fastly-Client-IP header")
    void shouldExtractFromFastlyClientIP() {
        Map<String, String> headers = Map.of("Fastly-Client-IP", "172.16.0.50");

        String result = ClientIpExtractor.extractClientIp(headers);

        assertEquals("172.16.0.50", result);
    }

    @Test
    @DisplayName("Should extract IP from X-Client-IP header")
    void shouldExtractFromXClientIP() {
        Map<String, String> headers = Map.of("X-Client-IP", "192.168.100.25");

        String result = ClientIpExtractor.extractClientIp(headers);

        assertEquals("192.168.100.25", result);
    }

    @Test
    @DisplayName("Should extract IP from X-Originating-IP header")
    void shouldExtractFromXOriginatingIP() {
        Map<String, String> headers = Map.of("X-Originating-IP", "10.1.1.1");

        String result = ClientIpExtractor.extractClientIp(headers);

        assertEquals("10.1.1.1", result);
    }

    @Test
    @DisplayName("Should extract IP from RFC 7239 Forwarded header")
    void shouldExtractFromForwardedHeader() {
        Map<String, String> headers = Map.of("Forwarded", "for=203.0.113.1; proto=https");

        String result = ClientIpExtractor.extractClientIp(headers);

        assertEquals("203.0.113.1", result);
    }

    @Test
    @DisplayName("Should extract IP from Forwarded header with quotes")
    void shouldExtractFromForwardedHeaderWithQuotes() {
        Map<String, String> headers = Map.of("Forwarded", "for=\"203.0.113.1\"; proto=https");

        String result = ClientIpExtractor.extractClientIp(headers);

        assertEquals("203.0.113.1", result);
    }

    @Test
    @DisplayName("Should extract IP from Forwarded header with brackets")
    void shouldExtractFromForwardedHeaderWithBrackets() {
        Map<String, String> headers = Map.of("Forwarded", "for=[2001:db8::1]; proto=https");

        String result = ClientIpExtractor.extractClientIp(headers);

        assertEquals("2001", result);
    }

    @Test
    @DisplayName("Should extract IP from Forwarded header with port")
    void shouldExtractFromForwardedHeaderWithPort() {
        Map<String, String> headers = Map.of("Forwarded", "for=203.0.113.1:8080; proto=https");

        String result = ClientIpExtractor.extractClientIp(headers);

        assertEquals("203.0.113.1", result);
    }

    @Test
    @DisplayName("Should handle empty header values")
    void shouldHandleEmptyHeaderValues() {
        Map<String, String> headers = Map.of(
                "X-Forwarded-For", "",
                "CF-Connecting-IP", "   ",
                "X-Real-IP", "192.168.1.1"
        );

        String result = ClientIpExtractor.extractClientIp(headers);

        assertEquals("192.168.1.1", result);
    }

    @Test
    @DisplayName("Should trim whitespace from extracted IP")
    void shouldTrimWhitespaceFromExtractedIP() {
        Map<String, String> headers = Map.of("X-Forwarded-For", "  192.168.1.1  ");

        String result = ClientIpExtractor.extractClientIp(headers);

        assertEquals("192.168.1.1", result);
    }

    @Test
    @DisplayName("Should follow priority order correctly")
    void shouldFollowPriorityOrder() {
        Map<String, String> headers = Map.of(
                "Remote-Addr", "10.0.0.1",           // Lowest priority
                "X-Real-IP", "10.0.0.2",            // Medium priority
                "CF-Connecting-IP", "10.0.0.3",     // High priority
                "X-Forwarded-For", "10.0.0.4"       // Highest priority
        );

        String result = ClientIpExtractor.extractClientIp(headers);

        assertEquals("10.0.0.4", result); // Should pick X-Forwarded-For (highest priority)
    }

    @Test
    @DisplayName("Should handle comma-separated values in multiple headers")
    void shouldHandleCommaSeparatedInMultipleHeaders() {
        Map<String, String> headers = Map.of(
                "X-Real-IP", "10.0.0.1,10.0.0.2",
                "X-Forwarded-For", "203.0.113.1,198.51.100.1"
        );

        String result = ClientIpExtractor.extractClientIp(headers);

        assertEquals("203.0.113.1", result); // Should pick first from X-Forwarded-For
    }

    @Test
    @DisplayName("Should handle malformed Forwarded header gracefully")
    void shouldHandleMalformedForwardedHeader() {
        Map<String, String> headers = Map.of(
                "Forwarded", "malformed=value; not-for=something",
                "X-Real-IP", "10.0.0.1"
        );

        String result = ClientIpExtractor.extractClientIp(headers);

        assertEquals("10.0.0.1", result); // Should fall back to X-Real-IP
    }

    @Test
    @DisplayName("Should handle IPv6 addresses")
    void shouldHandleIPv6Addresses() {
        Map<String, String> headers = Map.of("X-Forwarded-For", "2001:db8::1");

        String result = ClientIpExtractor.extractClientIp(headers);

        assertEquals("2001:db8::1", result);
    }

    @Test
    @DisplayName("Should handle mixed IPv4 and IPv6 in comma-separated values")
    void shouldHandleMixedIPVersions() {
        Map<String, String> headers = Map.of("X-Forwarded-For", "203.0.113.1,2001:db8::1");

        String result = ClientIpExtractor.extractClientIp(headers);

        assertEquals("203.0.113.1", result); // Should pick first IP
    }

    @Test
    @DisplayName("Should work with MultivaluedMap directly from JAX-RS")
    void shouldWorkWithMultivaluedMap() {
        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        headers.putSingle("CF-Connecting-IP", "203.0.113.100");
        headers.putSingle("X-Real-IP", "10.0.0.1");

        String result = ClientIpExtractor.extractClientIp(headers);

        assertEquals("203.0.113.100", result); // Should prioritize CF-Connecting-IP
    }

    @Test
    @DisplayName("Should handle MultivaluedMap with multiple values")
    void shouldHandleMultivaluedMapWithMultipleValues() {
        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        headers.add("X-Forwarded-For", "203.0.113.1,198.51.100.1");
        headers.add("X-Forwarded-For", "10.0.0.1"); // Second value should be ignored

        String result = ClientIpExtractor.extractClientIp(headers);

        assertEquals("203.0.113.1", result); // Should take first value, then first IP from comma-separated
    }
}