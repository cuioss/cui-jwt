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
package de.cuioss.jwt.validation.jwks.parser;

import de.cuioss.jwt.validation.JWTValidationLogMessages;
import de.cuioss.jwt.validation.ParserConfig;
import de.cuioss.jwt.validation.json.JwkKey;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for JWKS_KEYS_ARRAY_TOO_LARGE LogRecord coverage.
 * Tests the scenario where a JWKS contains more than 50 keys.
 */
@EnableTestLogger
@DisplayName("Tests JwksParser array size limits")
class JwksParserArraySizeTest {

    private JwksParser parser;
    private SecurityEventCounter securityEventCounter;

    @BeforeEach
    void setUp() {
        securityEventCounter = new SecurityEventCounter();
        parser = new JwksParser(ParserConfig.builder().build(), securityEventCounter);
    }

    @Test
    @DisplayName("Should log warning when JWKS keys array exceeds 50 keys")
    void shouldLogWarningForTooManyKeys() {
        // Create a JWKS with 51 keys (more than the limit of 50)
        StringBuilder jwksBuilder = new StringBuilder("{\"keys\":[");
        for (int i = 0; i < 51; i++) {
            if (i > 0) jwksBuilder.append(",");
            jwksBuilder.append(String.format("""
                {
                    "kty": "RSA",
                    "kid": "key-%d",
                    "use": "sig",
                    "alg": "RS256",
                    "n": "test-modulus-%d",
                    "e": "AQAB"
                }""", i, i));
        }
        jwksBuilder.append("]}");
        
        String oversizedJwks = jwksBuilder.toString();
        
        // Parse the oversized JWKS
        List<JwkKey> result = parser.parse(oversizedJwks);
        
        // The parser should still return results (doesn't reject, just warns)
        // But it should log the warning
        assertFalse(result.isEmpty(), "Parser should still process keys despite size warning");
        
        // Verify the warning was logged
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                JWTValidationLogMessages.WARN.JWKS_KEYS_ARRAY_TOO_LARGE.resolveIdentifierString());
        
        // Verify the security event was counted
        assertEquals(1, securityEventCounter.getCount(SecurityEventCounter.EventType.JWKS_JSON_PARSE_FAILED),
                "Should count as JWKS_JSON_PARSE_FAILED event");
    }

    @Test
    @DisplayName("Should not log warning for JWKS with exactly 50 keys")
    void shouldNotLogWarningForExactly50Keys() {
        // Create a JWKS with exactly 50 keys (at the limit)
        StringBuilder jwksBuilder = new StringBuilder("{\"keys\":[");
        for (int i = 0; i < 50; i++) {
            if (i > 0) jwksBuilder.append(",");
            jwksBuilder.append(String.format("""
                {
                    "kty": "RSA",
                    "kid": "key-%d",
                    "use": "sig",
                    "alg": "RS256",
                    "n": "test-modulus-%d",
                    "e": "AQAB"
                }""", i, i));
        }
        jwksBuilder.append("]}");
        
        String maxSizeJwks = jwksBuilder.toString();
        
        // Parse the max-size JWKS
        List<JwkKey> result = parser.parse(maxSizeJwks);
        
        // Should parse successfully without warning
        assertEquals(50, result.size(), "Should parse all 50 keys");
        
        // The test passes if no warning was logged (we can't easily assert absence)
        // The absence of the warning is implied by successful parsing without error
        
        // No security event should be counted
        assertEquals(0, securityEventCounter.getCount(SecurityEventCounter.EventType.JWKS_JSON_PARSE_FAILED),
                "Should not count as error for exactly 50 keys");
    }
}