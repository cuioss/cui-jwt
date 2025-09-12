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
package de.cuioss.jwt.validation.well_known;

import de.cuioss.jwt.validation.test.dispatcher.WellKnownDispatcher;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import de.cuioss.tools.net.http.client.LoaderStatus;
import de.cuioss.tools.net.http.retry.RetryStrategy;
import lombok.Getter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@EnableTestLogger
@DisplayName("HttpWellKnownResolver Issuer String Tests")
@EnableMockWebServer
class HttpWellKnownResolverIssuerTest {

    @Getter
    private final WellKnownDispatcher moduleDispatcher = new WellKnownDispatcher();

    private HttpWellKnownResolver resolver;

    @BeforeEach
    void setUp() {
        moduleDispatcher.setCallCounter(0);
    }

    @Test
    @DisplayName("Should return issuer string from successful well-known response")
    void shouldReturnIssuerStringFromSuccessfulResponse(URIBuilder uriBuilder) {
        // Setup dispatcher with valid response
        moduleDispatcher.returnDefault();

        WellKnownConfig config = WellKnownConfig.builder()
                .wellKnownUrl(uriBuilder.addPathSegment(".well-known").addPathSegment("openid-configuration").buildAsString())
                .retryStrategy(RetryStrategy.exponentialBackoff())
                .build();

        resolver = new HttpWellKnownResolver(config);

        // Verify issuer is returned as a string
        Optional<String> issuer = resolver.getIssuer();
        assertTrue(issuer.isPresent(), "Issuer should be present");
        assertNotNull(issuer.get(), "Issuer should not be null");
        assertTrue(issuer.get().startsWith("http"), "Issuer should be a URL string");

        // Verify health status is OK
        assertEquals(LoaderStatus.OK, resolver.isHealthy());
    }

    @Test
    @DisplayName("Should return empty Optional when issuer is missing from response")
    void shouldReturnEmptyWhenIssuerMissing(URIBuilder uriBuilder) {
        // Setup dispatcher with response missing issuer
        moduleDispatcher.returnMissingIssuer();

        WellKnownConfig config = WellKnownConfig.builder()
                .wellKnownUrl(uriBuilder.addPathSegment(".well-known").addPathSegment("openid-configuration").buildAsString())
                .retryStrategy(RetryStrategy.exponentialBackoff())
                .build();

        resolver = new HttpWellKnownResolver(config);

        // Verify issuer is empty
        Optional<String> issuer = resolver.getIssuer();
        assertFalse(issuer.isPresent(), "Issuer should not be present when missing from response");

        // Verify health status is ERROR since issuer is required
        assertEquals(LoaderStatus.UNDEFINED, resolver.isHealthy());
    }

    @Test
    @DisplayName("Should handle issuer mismatch validation")
    void shouldHandleIssuerMismatchValidation(URIBuilder uriBuilder) {
        // Setup dispatcher with invalid issuer (mismatch)
        moduleDispatcher.returnInvalidIssuer();

        WellKnownConfig config = WellKnownConfig.builder()
                .wellKnownUrl(uriBuilder.addPathSegment(".well-known").addPathSegment("openid-configuration").buildAsString())
                .retryStrategy(RetryStrategy.exponentialBackoff())
                .build();

        resolver = new HttpWellKnownResolver(config);

        // When issuer validation fails, issuer should not be set
        Optional<String> issuer = resolver.getIssuer();
        assertFalse(issuer.isPresent(), "Issuer should not be present when validation fails");

        // Verify health status is ERROR
        assertEquals(LoaderStatus.UNDEFINED, resolver.isHealthy());
    }

    @Test
    @DisplayName("Should cache issuer after successful load")
    void shouldCacheIssuerAfterSuccessfulLoad(URIBuilder uriBuilder) {
        // Setup dispatcher with valid response
        moduleDispatcher.returnDefault();

        WellKnownConfig config = WellKnownConfig.builder()
                .wellKnownUrl(uriBuilder.addPathSegment(".well-known").addPathSegment("openid-configuration").buildAsString())
                .retryStrategy(RetryStrategy.exponentialBackoff())
                .build();

        resolver = new HttpWellKnownResolver(config);

        // First call - should load from server
        Optional<String> issuer1 = resolver.getIssuer();
        assertTrue(issuer1.isPresent());

        // Second call - should return cached value
        Optional<String> issuer2 = resolver.getIssuer();
        assertTrue(issuer2.isPresent());

        // Both should be the same
        assertEquals(issuer1.get(), issuer2.get());

        // Verify only one request was made
        assertEquals(1, moduleDispatcher.getCallCounter());
    }

    @Test
    @DisplayName("Should handle concurrent access to issuer")
    void shouldHandleConcurrentAccessToIssuer(URIBuilder uriBuilder) throws InterruptedException {
        // Setup dispatcher with valid response
        moduleDispatcher.returnDefault();

        WellKnownConfig config = WellKnownConfig.builder()
                .wellKnownUrl(uriBuilder.addPathSegment(".well-known").addPathSegment("openid-configuration").buildAsString())
                .retryStrategy(RetryStrategy.exponentialBackoff())
                .build();

        resolver = new HttpWellKnownResolver(config);

        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        @SuppressWarnings("unchecked") Optional<String>[] results = new Optional[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                results[index] = resolver.getIssuer();
            });
        }

        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // All threads should get the same issuer value
        for (int i = 0; i < threadCount; i++) {
            assertTrue(results[i].isPresent(), "Thread " + i + " should have received issuer");
            assertEquals(results[0].get(), results[i].get(), "All threads should receive the same issuer");
        }

        // Should only make one request despite concurrent access
        assertEquals(1, moduleDispatcher.getCallCounter());
    }
}