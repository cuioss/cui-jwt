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

class TokenRepositoryTest {

    @Test void constructorWithNullConfig() {
        assertThrows(NullPointerException.class, () -> new TokenRepository(null));
    }

    @Test void tokenFetchException() {
        // Test the custom exception
        TokenRepository.TokenFetchException ex = new TokenRepository.TokenFetchException("Test error");
        assertEquals("Test error", ex.getMessage());

        Exception cause = new RuntimeException("Cause");
        TokenRepository.TokenFetchException exWithCause = new TokenRepository.TokenFetchException("Test error", cause);
        assertEquals("Test error", exWithCause.getMessage());
        assertEquals(cause, exWithCause.getCause());
    }

    @Test void configBuilderDefaults() {
        // Test that we can build a config with defaults
        TokenRepositoryConfig config = TokenRepositoryConfig.builder().build();
        assertNotNull(config);
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

    @Test void configBuilderCustomValues() {
        TokenRepositoryConfig config = TokenRepositoryConfig.builder()
                .keycloakBaseUrl("https://auth.example.com")
                .realm("custom-realm")
                .clientId("custom-client")
                .clientSecret("custom-secret")
                .username("custom-user")
                .password("custom-pass")
                .tokenPoolSize(50)
                .connectionTimeoutMs(3000)
                .requestTimeoutMs(15000)
                .verifySsl(true)
                .tokenRefreshThresholdSeconds(300)
                .build();

        assertEquals("https://auth.example.com", config.getKeycloakBaseUrl());
        assertEquals("custom-realm", config.getRealm());
        assertEquals("custom-client", config.getClientId());
        assertEquals("custom-secret", config.getClientSecret());
        assertEquals("custom-user", config.getUsername());
        assertEquals("custom-pass", config.getPassword());
        assertEquals(50, config.getTokenPoolSize());
        assertEquals(3000, config.getConnectionTimeoutMs());
        assertEquals(15000, config.getRequestTimeoutMs());
        assertTrue(config.isVerifySsl());
        assertEquals(300, config.getTokenRefreshThresholdSeconds());
    }
}