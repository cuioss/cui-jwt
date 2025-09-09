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
package de.cuioss.tools.net.http.client;

import de.cuioss.jwt.validation.well_known.WellKnownConfig;
import de.cuioss.tools.net.http.HttpHandler;
import de.cuioss.tools.net.http.retry.RetryStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test for ETagAwareHttpHandler with HttpHandlerProvider pattern.
 */
@DisplayName("ETagAwareHttpHandler HttpHandlerProvider Integration Tests")
class ETagAwareHttpHandlerProviderTest {

    private static final String TEST_URL = "https://test.example.com/.well-known/openid_configuration";

    @Test
    @DisplayName("Should create ETagAwareHttpHandler with HttpHandlerProvider")
    void shouldCreateWithHttpHandlerProvider() {
        // Given: A WellKnownConfig that implements HttpHandlerProvider
        RetryStrategy retryStrategy = RetryStrategy.exponentialBackoff();
        WellKnownConfig config = WellKnownConfig.builder()
                .wellKnownUrl(TEST_URL)
                .retryStrategy(retryStrategy)
                .build();

        // When: Creating ETagAwareHttpHandler with HttpHandlerProvider
        ETagAwareHttpHandler<String> handler = ETagAwareHttpHandler.forString(config);

        // Then: Should be created successfully
        assertNotNull(handler, "ETagAwareHttpHandler should be created");
    }

    @Test
    @DisplayName("Should create ETagAwareHttpHandler with custom HttpHandlerProvider")
    void shouldCreateWithCustomProvider() {
        // Given: A custom HttpHandlerProvider implementation
        HttpHandlerProvider provider = new HttpHandlerProvider() {
            @Override
            public HttpHandler getHttpHandler() {
                return HttpHandler.builder()
                        .url(TEST_URL)
                        .build();
            }

            @Override
            public RetryStrategy getRetryStrategy() {
                return RetryStrategy.none();
            }
        };

        // When: Creating ETagAwareHttpHandler with custom provider
        ETagAwareHttpHandler<String> handler = ETagAwareHttpHandler.forString(provider);

        // Then: Should be created successfully
        assertNotNull(handler, "ETagAwareHttpHandler should be created with custom provider");
    }

    @Test
    @DisplayName("Should maintain backward compatibility with legacy constructor")
    void shouldMaintainBackwardCompatibility() {
        // Given: A direct HttpHandler (legacy usage)
        HttpHandler httpHandler = HttpHandler.builder()
                .url(TEST_URL)
                .build();

        // When: Creating ETagAwareHttpHandler with direct HttpHandler constructor
        ETagAwareHttpHandler<String> handler = ETagAwareHttpHandler.forString(httpHandler);

        // Then: Should still work for backward compatibility
        assertNotNull(handler, "ETagAwareHttpHandler should maintain backward compatibility");
    }

    @Test
    @DisplayName("Should fail when HttpHandlerProvider is null")
    void shouldFailWhenProviderIsNull() {
        // Given: A null HttpHandlerProvider
        HttpHandlerProvider provider = null;

        // When/Then: Should throw IllegalArgumentException
        assertThrows(NullPointerException.class, () -> {
            ETagAwareHttpHandler.forString(provider);
        }, "Should throw exception when provider is null");
    }
}