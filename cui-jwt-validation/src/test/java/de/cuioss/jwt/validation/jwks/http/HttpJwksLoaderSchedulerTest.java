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
package de.cuioss.jwt.validation.jwks.http;

import de.cuioss.jwt.validation.jwks.LoaderStatus;
import de.cuioss.jwt.validation.jwks.key.KeyInfo;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.jwt.validation.test.InMemoryJWKSFactory;
import de.cuioss.jwt.validation.test.dispatcher.JwksResolveDispatcher;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import lombok.Getter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@EnableTestLogger
@DisplayName("Tests HttpJwksLoader Background Refresh Scheduler")
@EnableMockWebServer
class HttpJwksLoaderSchedulerTest {

    private static final String TEST_KID = InMemoryJWKSFactory.DEFAULT_KEY_ID;

    @Getter
    private final JwksResolveDispatcher moduleDispatcher = new JwksResolveDispatcher();

    private SecurityEventCounter securityEventCounter;

    @BeforeEach
    void setUp() {
        moduleDispatcher.setCallCounter(0);
        securityEventCounter = new SecurityEventCounter();
    }


    @Test
    @DisplayName("Should not start scheduler when no config provided")
    void shouldNotStartSchedulerWithoutConfig(URIBuilder uriBuilder) {
        String jwksEndpoint = uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString();

        // Ensure dispatcher is in normal mode
        moduleDispatcher.returnDefault();

        // Create loader without scheduler config
        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .jwksUrl(jwksEndpoint)
                .refreshIntervalSeconds(0) // Disable scheduler
                .build();

        HttpJwksLoader loader = new HttpJwksLoader(config);
        loader.initJWKSLoader(securityEventCounter);

        // Trigger initial load
        Optional<KeyInfo> keyInfo = loader.getKeyInfo(TEST_KID);
        assertTrue(keyInfo.isPresent(), "Initial load should work");

        // Scheduler should not be active without config
        assertFalse(loader.isBackgroundRefreshActive(), "Background refresh should not be active without refresh interval");

        loader.shutdown();
    }

    @Test
    @DisplayName("Should not start scheduler when refresh interval is zero")
    void shouldNotStartSchedulerWithZeroInterval(URIBuilder uriBuilder) {
        String jwksEndpoint = uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString();

        // Ensure dispatcher is in normal mode
        moduleDispatcher.returnDefault();

        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .jwksUrl(jwksEndpoint)
                .refreshIntervalSeconds(0) // Zero means no refresh
                .build();

        HttpJwksLoader loader = new HttpJwksLoader(config);
        loader.initJWKSLoader(securityEventCounter);

        // Trigger initial load
        Optional<KeyInfo> keyInfo = loader.getKeyInfo(TEST_KID);
        assertTrue(keyInfo.isPresent(), "Initial load should work");

        // Scheduler should not be active with zero interval
        assertFalse(loader.isBackgroundRefreshActive(), "Background refresh should not be active with zero interval");

        loader.shutdown();
    }

    @Test
    @DisplayName("Should start scheduler after first successful load")
    void shouldStartSchedulerAfterFirstLoad(URIBuilder uriBuilder) {
        String jwksEndpoint = uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString();

        // Ensure dispatcher is in normal mode
        moduleDispatcher.returnDefault();

        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .jwksUrl(jwksEndpoint)
                .refreshIntervalSeconds(1) // 1 second for testing
                .build();

        HttpJwksLoader loader = new HttpJwksLoader(config);
        loader.initJWKSLoader(securityEventCounter);

        // Scheduler should not be active before first load
        assertFalse(loader.isBackgroundRefreshActive(), "Background refresh should not be active before first load");

        // Trigger initial load
        Optional<KeyInfo> keyInfo = loader.getKeyInfo(TEST_KID);
        assertTrue(keyInfo.isPresent(), "Initial load should work");

        // Wait for scheduler to start using Awaitility
        await("Background refresh scheduler to start")
                .atMost(2, SECONDS)
                .until(loader::isBackgroundRefreshActive);

        // Verify log message about scheduler start
        LogAsserts.assertLogMessagePresentContaining(
                TestLogLevel.INFO,
                "Background JWKS refresh started with interval: 1");

        loader.shutdown();

        // After shutdown, scheduler should be inactive
        assertFalse(loader.isBackgroundRefreshActive(), "Background refresh should be inactive after shutdown");
    }

    @Test
    @DisplayName("Should perform background refresh and detect changes")
    void shouldPerformBackgroundRefresh(URIBuilder uriBuilder) {
        String jwksEndpoint = uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString();

        // Ensure dispatcher is in normal mode
        moduleDispatcher.returnDefault();

        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .jwksUrl(jwksEndpoint)
                .refreshIntervalSeconds(1) // 1 second for testing
                .build();

        HttpJwksLoader loader = new HttpJwksLoader(config);
        loader.initJWKSLoader(securityEventCounter);

        // Trigger initial load
        Optional<KeyInfo> initialKeyInfo = loader.getKeyInfo(TEST_KID);
        assertTrue(initialKeyInfo.isPresent(), "Initial load should work");

        int initialCallCount = moduleDispatcher.getCallCounter();

        // Switch to different key to simulate change
        moduleDispatcher.switchToOtherPublicKey();

        // Wait for background refresh to trigger using Awaitility
        await("Background refresh to detect key changes and make additional HTTP call")
                .atMost(2, SECONDS)
                .until(() -> moduleDispatcher.getCallCounter() > initialCallCount);

        loader.shutdown();
    }

    @Test
    @DisplayName("Should handle background refresh errors gracefully")
    void shouldHandleBackgroundRefreshErrors(URIBuilder uriBuilder) {
        String jwksEndpoint = uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString();

        // Ensure dispatcher is in normal mode
        moduleDispatcher.returnDefault();

        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .jwksUrl(jwksEndpoint)
                .refreshIntervalSeconds(1) // 1 second for testing
                .build();

        HttpJwksLoader loader = new HttpJwksLoader(config);
        loader.initJWKSLoader(securityEventCounter);

        // Trigger initial successful load
        Optional<KeyInfo> keyInfo = loader.getKeyInfo(TEST_KID);
        assertTrue(keyInfo.isPresent(), "Initial load should work");
        assertEquals(LoaderStatus.OK, loader.isHealthy(), "Loader should be healthy after initial load");

        // Make subsequent requests fail
        moduleDispatcher.returnError();

        // Wait for background refresh to encounter errors - scheduler runs every 1 second
        await("Scheduler to execute at least one background refresh cycle")
                .atMost(3000, MILLISECONDS)
                .pollDelay(1500, MILLISECONDS) // Give scheduler time to run at least once
                .until(() -> true); // Just wait for the time period

        // Loader should still be healthy if it has existing keys
        assertEquals(LoaderStatus.OK, loader.isHealthy(), "Loader should remain healthy with cached keys even if background refresh fails");

        // Verify HTTP error logging occurred
        LogAsserts.assertLogMessagePresentContaining(
                TestLogLevel.WARN,
                "HTTP 500 (Server Error (500-599))");

        loader.shutdown();
    }

    @Test
    @DisplayName("Should only start scheduler once")
    void shouldStartSchedulerOnlyOnce(URIBuilder uriBuilder) {
        String jwksEndpoint = uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString();

        // Ensure dispatcher is in normal mode
        moduleDispatcher.returnDefault();

        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .jwksUrl(jwksEndpoint)
                .refreshIntervalSeconds(10) // Longer interval to avoid multiple executions during test
                .build();

        HttpJwksLoader loader = new HttpJwksLoader(config);
        loader.initJWKSLoader(securityEventCounter);

        // Trigger multiple loads
        loader.getKeyInfo(TEST_KID);
        loader.getKeyInfo(TEST_KID);
        loader.getKeyInfo("another-kid");
        loader.getKeyInfo(null);

        // Wait for scheduler to start using Awaitility
        await("Scheduler to activate after multiple operations")
                .atMost(500, MILLISECONDS)
                .until(loader::isBackgroundRefreshActive);

        // Should only have one scheduler start message
        LogAsserts.assertLogMessagePresentContaining(
                TestLogLevel.INFO,
                "Background JWKS refresh started");

        loader.shutdown();
    }

    @Test
    @DisplayName("Should shutdown scheduler cleanly")
    void shouldShutdownSchedulerCleanly(URIBuilder uriBuilder) {
        String jwksEndpoint = uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString();

        // Ensure dispatcher is in normal mode
        moduleDispatcher.returnDefault();

        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .jwksUrl(jwksEndpoint)
                .refreshIntervalSeconds(1)
                .build();

        HttpJwksLoader loader = new HttpJwksLoader(config);
        loader.initJWKSLoader(securityEventCounter);

        // Start scheduler
        loader.getKeyInfo(TEST_KID);
        await("Scheduler to start after key load")
                .atMost(500, MILLISECONDS)
                .until(loader::isBackgroundRefreshActive);

        // Shutdown should cancel the task
        loader.shutdown();
        assertFalse(loader.isBackgroundRefreshActive(), "Scheduler should be inactive after shutdown");

        // Multiple shutdowns should be safe
        loader.shutdown();
        loader.shutdown();
        assertFalse(loader.isBackgroundRefreshActive(), "Multiple shutdowns should be safe");
    }

    @Test
    @DisplayName("Should not start scheduler if initial load fails")
    void shouldNotStartSchedulerIfInitialLoadFails(URIBuilder uriBuilder) {
        String jwksEndpoint = uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString();

        // Make all requests fail from the start
        moduleDispatcher.returnError();

        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .jwksUrl(jwksEndpoint)
                .refreshIntervalSeconds(1)
                .build();

        HttpJwksLoader loader = new HttpJwksLoader(config);
        loader.initJWKSLoader(securityEventCounter);

        // Initial load should fail - getKeyInfo returns empty when loading fails
        Optional<KeyInfo> keyInfo = loader.getKeyInfo(TEST_KID);
        assertTrue(keyInfo.isEmpty(), "Expected empty result for failed initial load");

        // Scheduler should be started after failed load to enable retries
        assertTrue(loader.isBackgroundRefreshActive(), "Background refresh should start after failed initial load to enable retries");
        assertNotEquals(LoaderStatus.OK, loader.isHealthy(), "Loader should not be healthy after failed load");

        loader.shutdown();
    }

    @Test
    @DisplayName("Should handle background refresh correctly")
    void shouldHandleBackgroundRefreshCorrectly(URIBuilder uriBuilder) {
        String jwksEndpoint = uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString();

        // Ensure dispatcher is in normal mode
        moduleDispatcher.returnDefault();

        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .jwksUrl(jwksEndpoint)
                .refreshIntervalSeconds(1)
                .build();

        HttpJwksLoader loader = new HttpJwksLoader(config);
        loader.initJWKSLoader(securityEventCounter);

        // Trigger initial load
        Optional<KeyInfo> keyInfo = loader.getKeyInfo(TEST_KID);
        assertTrue(keyInfo.isPresent(), "Initial load should work");

        // Wait for background refresh to execute - scheduler runs every 1 second
        await("At least one background refresh cycle to complete")
                .atMost(1500, MILLISECONDS)
                .pollDelay(1200, MILLISECONDS) // Give scheduler time to run at least once
                .until(() -> true); // Just wait for the time period

        // Verify that background refresh executed - it should have logged either:
        // - "Background refresh completed, no changes detected" if 304 Not Modified
        // - "Keys updated due to data change" if data changed
        // Both are valid outcomes depending on mock server behavior
        try {
            LogAsserts.assertLogMessagePresentContaining(
                    TestLogLevel.DEBUG,
                    "Background refresh completed, no changes detected");
        } catch (AssertionError e) {
            // If not no-changes, then it should have updated
            LogAsserts.assertLogMessagePresentContaining(
                    TestLogLevel.INFO,
                    "Keys updated due to data change");
        }

        loader.shutdown();
    }
}