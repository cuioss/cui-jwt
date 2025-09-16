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
package de.cuioss.http.client;

import de.cuioss.http.client.retry.RetryStrategy;
import de.cuioss.jwt.validation.jwks.http.HttpJwksLoaderConfig;
import de.cuioss.jwt.validation.well_known.WellKnownConfig;
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

        // Then: Should provide both HttpHandler and RetryStrategy
        assertNotNull(config.getHttpHandler(), "HttpHandler should not be null");
        assertNotNull(config.getRetryStrategy(), "RetryStrategy should not be null");
        assertSame(retryStrategy, config.getRetryStrategy(), "Should return the same RetryStrategy instance");
    }

    @Test
    void wellKnownConfig_withoutRetryStrategy_shouldUseDefault() {
        // Given: A builder without explicit RetryStrategy
        WellKnownConfig config = WellKnownConfig.builder()
                .wellKnownUrl(TEST_URL)
                .build();

        // Then: Should provide default RetryStrategy (exponentialBackoff)
        assertNotNull(config.getHttpHandler(), "HttpHandler should not be null");
        assertNotNull(config.getRetryStrategy(), "Should provide default RetryStrategy");
        // The default is exponentialBackoff, so it should not be the no-op strategy
        assertNotSame(RetryStrategy.none(), config.getRetryStrategy(), "Default should not be no-op strategy");
    }

    @Test
    void wellKnownConfig_withNoOpRetryStrategy_shouldWork() {
        // Given: A WellKnownConfig with no-op retry strategy
        RetryStrategy noOpStrategy = RetryStrategy.none();

        WellKnownConfig config = WellKnownConfig.builder()
                .wellKnownUrl(TEST_URL)
                .retryStrategy(noOpStrategy)
                .build();

        // Then: Should work correctly
        assertNotNull(config.getHttpHandler());
        assertSame(noOpStrategy, config.getRetryStrategy());
    }

    @Test
    void httpJwksLoaderConfig_directMode_shouldImplementHttpHandlerProvider() {
        // Given: HttpJwksLoaderConfig in direct HTTP mode
        RetryStrategy retryStrategy = RetryStrategy.exponentialBackoff();

        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .jwksUrl(TEST_URL.replace("/.well-known/openid_configuration", "/jwks"))
                .issuerIdentifier("test-issuer")
                .retryStrategy(retryStrategy)
                .build();

        // Then: Should provide both HttpHandler and RetryStrategy
        assertNotNull(config.getHttpHandler(), "HttpHandler should not be null in direct mode");
        assertNotNull(config.getRetryStrategy(), "RetryStrategy should not be null");
        assertSame(retryStrategy, config.getRetryStrategy(), "Should return the same RetryStrategy instance");
    }

    @Test
    void httpJwksLoaderConfig_wellKnownMode_shouldImplementHttpHandlerProvider() {
        // Given: HttpJwksLoaderConfig in well-known discovery mode
        RetryStrategy retryStrategy = RetryStrategy.exponentialBackoff();

        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .wellKnownUrl(TEST_URL)
                .retryStrategy(retryStrategy)
                .build();

        // Then: Should provide both HttpHandler and RetryStrategy
        assertNotNull(config.getHttpHandler(), "HttpHandler should not be null in well-known mode");
        assertNotNull(config.getRetryStrategy(), "RetryStrategy should not be null");
        assertSame(retryStrategy, config.getRetryStrategy(), "Should return the same RetryStrategy instance");
    }

    @Test
    void httpJwksLoaderConfig_withoutRetryStrategy_shouldUseDefault() {
        // Given: HttpJwksLoaderConfig without explicit RetryStrategy
        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .jwksUrl(TEST_URL.replace("/.well-known/openid_configuration", "/jwks"))
                .issuerIdentifier("test-issuer")
                .build();

        // Then: Should provide default RetryStrategy
        assertNotNull(config.getHttpHandler(), "HttpHandler should not be null");
        assertNotNull(config.getRetryStrategy(), "Should provide default RetryStrategy");
    }
}