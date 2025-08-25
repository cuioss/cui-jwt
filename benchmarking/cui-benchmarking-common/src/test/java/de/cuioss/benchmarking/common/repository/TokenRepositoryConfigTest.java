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
package de.cuioss.benchmarking.common.repository;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenRepositoryConfigTest {

    @Test void defaultValues() {
        TokenRepositoryConfig config = TokenRepositoryConfig.builder().build();

        assertEquals("https://localhost:1443", config.getKeycloakBaseUrl());
        assertEquals("benchmark", config.getRealm());
        assertEquals("benchmark-client", config.getClientId());
        assertEquals("benchmark-secret", config.getClientSecret());
        assertEquals("benchmark-user", config.getUsername());
        assertEquals("benchmark-password", config.getPassword());
        assertEquals(100, config.getTokenPoolSize());
        assertEquals(5000, config.getConnectionTimeoutMs());
        assertEquals(10000, config.getRequestTimeoutMs());
        assertFalse(config.isVerifySsl());
        assertEquals(180, config.getTokenRefreshThresholdSeconds());
    }

    @Test void customValues() {
        TokenRepositoryConfig config = TokenRepositoryConfig.builder()
                .keycloakBaseUrl("https://auth.example.com")
                .realm("test-realm")
                .clientId("test-client")
                .clientSecret("test-secret")
                .username("test-user")
                .password("test-pass")
                .tokenPoolSize(200)
                .connectionTimeoutMs(3000)
                .requestTimeoutMs(15000)
                .verifySsl(true)
                .tokenRefreshThresholdSeconds(300)
                .build();

        assertEquals("https://auth.example.com", config.getKeycloakBaseUrl());
        assertEquals("test-realm", config.getRealm());
        assertEquals("test-client", config.getClientId());
        assertEquals("test-secret", config.getClientSecret());
        assertEquals("test-user", config.getUsername());
        assertEquals("test-pass", config.getPassword());
        assertEquals(200, config.getTokenPoolSize());
        assertEquals(3000, config.getConnectionTimeoutMs());
        assertEquals(15000, config.getRequestTimeoutMs());
        assertTrue(config.isVerifySsl());
        assertEquals(300, config.getTokenRefreshThresholdSeconds());
    }

    @Test void partialBuilder() {
        TokenRepositoryConfig config = TokenRepositoryConfig.builder()
                .keycloakBaseUrl("https://partial.com")
                .realm("partial-realm")
                .tokenPoolSize(50)
                .build();

        // Verify partial configuration with defaults
        assertEquals("https://partial.com", config.getKeycloakBaseUrl());
        assertEquals("partial-realm", config.getRealm());
        assertEquals(50, config.getTokenPoolSize());
        // Defaults should be preserved
        assertEquals("benchmark-client", config.getClientId());
        assertEquals("benchmark-secret", config.getClientSecret());
    }

    @Test void equalsAndHashCode() {
        TokenRepositoryConfig config1 = TokenRepositoryConfig.builder()
                .keycloakBaseUrl("https://test.com")
                .realm("test")
                .build();

        TokenRepositoryConfig config2 = TokenRepositoryConfig.builder()
                .keycloakBaseUrl("https://test.com")
                .realm("test")
                .build();

        TokenRepositoryConfig config3 = TokenRepositoryConfig.builder()
                .keycloakBaseUrl("https://different.com")
                .realm("test")
                .build();

        assertEquals(config1, config2);
        assertEquals(config1.hashCode(), config2.hashCode());
        assertNotEquals(config1, config3);
    }

    @Test void testToString() {
        TokenRepositoryConfig config = TokenRepositoryConfig.builder()
                .keycloakBaseUrl("https://test.com")
                .realm("test-realm")
                .build();

        String str = config.toString();
        assertNotNull(str);
        assertTrue(str.contains("https://test.com"));
        assertTrue(str.contains("test-realm"));
    }
}