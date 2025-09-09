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
package de.cuioss.tools.net.http;

import de.cuioss.jwt.validation.jwks.http.HttpJwksLoaderConfig;
import de.cuioss.jwt.validation.well_known.WellKnownConfig;
import de.cuioss.tools.net.http.retry.RetryStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for the HttpHandlerProvider interface and its implementations.
 */
class HttpHandlerProviderTest {

    private static final String TEST_URL = "https://test.example.com/.well-known/openid_configuration";

    @Test
    void wellKnownConfig_shouldImplementHttpHandlerProvider() {
        // Given: A WellKnownConfig with RetryStrategy
        RetryStrategy retryStrategy = RetryStrategy.exponentialBackoff();

        WellKnownConfig config = WellKnownConfig.builder()
                .wellKnownUrl(TEST_URL)
                .retryStrategy(retryStrategy)
                .build();

        // When: Casting to HttpHandlerProvider interface
        HttpHandlerProvider provider = config;

        // Then: Should provide both HttpHandler and RetryStrategy
        assertNotNull(provider.getHttpHandler(), "HttpHandler should not be null");
        assertNotNull(provider.getRetryStrategy(), "RetryStrategy should not be null");
        assertSame(retryStrategy, provider.getRetryStrategy(), "Should return the same RetryStrategy instance");
    }

    @Test
    void wellKnownConfig_withoutRetryStrategy_shouldFailToBuild() {
        // Given: A builder without RetryStrategy
        WellKnownConfig.WellKnownConfigBuilder builder = WellKnownConfig.builder()
                .wellKnownUrl(TEST_URL);

        // When/Then: Should fail to build
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                builder::build,
                "Should throw IllegalArgumentException when RetryStrategy is missing");

        assertTrue(exception.getMessage().contains("RetryStrategy is required"),
                "Error message should indicate RetryStrategy is required");
    }

    @Test
    void wellKnownConfig_withNoOpRetryStrategy_shouldWork() {
        // Given: A WellKnownConfig with no-op retry strategy
        RetryStrategy noOpStrategy = RetryStrategy.none();

        WellKnownConfig config = WellKnownConfig.builder()
                .wellKnownUrl(TEST_URL)
                .retryStrategy(noOpStrategy)
                .build();

        // When: Using as HttpHandlerProvider
        HttpHandlerProvider provider = config;

        // Then: Should work correctly
        assertNotNull(provider.getHttpHandler());
        assertSame(noOpStrategy, provider.getRetryStrategy());
    }

    @Test
    void httpJwksLoaderConfig_directMode_shouldImplementHttpHandlerProvider() {
        // Given: HttpJwksLoaderConfig in direct HTTP mode
        RetryStrategy retryStrategy = RetryStrategy.exponentialBackoff();

        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .jwksUrl(TEST_URL.replace("/.well-known/openid_configuration", "/jwks"))
                .retryStrategy(retryStrategy)
                .build();

        // When: Casting to HttpHandlerProvider interface
        HttpHandlerProvider provider = config;

        // Then: Should provide both HttpHandler and RetryStrategy
        assertNotNull(provider.getHttpHandler(), "HttpHandler should not be null in direct mode");
        assertNotNull(provider.getRetryStrategy(), "RetryStrategy should not be null");
        assertSame(retryStrategy, provider.getRetryStrategy(), "Should return the same RetryStrategy instance");
    }

    @Test
    void httpJwksLoaderConfig_wellKnownMode_shouldImplementHttpHandlerProvider() {
        // Given: HttpJwksLoaderConfig in well-known discovery mode
        RetryStrategy retryStrategy = RetryStrategy.exponentialBackoff();

        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .wellKnownUrl(TEST_URL)
                .retryStrategy(retryStrategy)
                .build();

        // When: Casting to HttpHandlerProvider interface
        HttpHandlerProvider provider = config;

        // Then: Should provide both HttpHandler and RetryStrategy
        assertNotNull(provider.getHttpHandler(), "HttpHandler should not be null in well-known mode");
        assertNotNull(provider.getRetryStrategy(), "RetryStrategy should not be null");
        assertSame(retryStrategy, provider.getRetryStrategy(), "Should return the same RetryStrategy instance");
    }

    @Test
    void httpJwksLoaderConfig_withoutRetryStrategy_shouldUseDefault() {
        // Given: HttpJwksLoaderConfig without explicit RetryStrategy
        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .jwksUrl(TEST_URL.replace("/.well-known/openid_configuration", "/jwks"))
                .build();

        // When: Using as HttpHandlerProvider
        HttpHandlerProvider provider = config;

        // Then: Should provide default RetryStrategy
        assertNotNull(provider.getHttpHandler(), "HttpHandler should not be null");
        assertNotNull(provider.getRetryStrategy(), "Should provide default RetryStrategy");
    }
}