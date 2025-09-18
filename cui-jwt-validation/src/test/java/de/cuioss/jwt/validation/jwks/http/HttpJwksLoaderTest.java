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

import de.cuioss.http.client.HttpLogMessages;
import de.cuioss.http.client.LoaderStatus;
import de.cuioss.jwt.validation.JWTValidationLogMessages;
import de.cuioss.jwt.validation.jwks.key.KeyInfo;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.jwt.validation.test.InMemoryJWKSFactory;
import de.cuioss.jwt.validation.test.dispatcher.JwksResolveDispatcher;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcher;
import lombok.Getter;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@EnableTestLogger
@DisplayName("Tests HttpJwksLoader")
@EnableMockWebServer
class HttpJwksLoaderTest {

    private static final String TEST_KID = InMemoryJWKSFactory.DEFAULT_KEY_ID;

    @Getter
    private final JwksResolveDispatcher moduleDispatcher = new JwksResolveDispatcher();

    private HttpJwksLoader httpJwksLoader;
    private SecurityEventCounter securityEventCounter;

    @BeforeEach
    void setUp(URIBuilder uriBuilder) {
        String jwksEndpoint = uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString();
        moduleDispatcher.setCallCounter(0);

        // Initialize the SecurityEventCounter
        securityEventCounter = new SecurityEventCounter();

        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .jwksUrl(jwksEndpoint)
                .issuerIdentifier("test-issuer")
                .build();

        httpJwksLoader = new HttpJwksLoader(config);
        // Wait for async initialization to complete
        httpJwksLoader.initJWKSLoader(securityEventCounter).join();
    }

    @Test
    @DisplayName("Should create loader with constructor")
    void shouldCreateLoaderWithConstructor() {
        assertNotNull(httpJwksLoader, "HttpJwksLoader should not be null");
        // Simplified loader doesn't expose config - just verify it was created
        assertNotNull(httpJwksLoader.getLoaderStatus(), "Status should be available");
    }

    @Test
    @DisplayName("Should get key info by ID")
    void shouldGetKeyInfoById() {

        Optional<KeyInfo> keyInfo = httpJwksLoader.getKeyInfo(TEST_KID);
        assertTrue(keyInfo.isPresent(), "Key info should be present");
        assertEquals(TEST_KID, keyInfo.get().keyId(), "Key ID should match");
        assertEquals(1, moduleDispatcher.getCallCounter(), "JWKS endpoint should be called once");
    }

    @Test
    @DisplayName("Should return empty for unknown key ID")
    void shouldReturnEmptyForUnknownKeyId() {
        Optional<KeyInfo> keyInfo = httpJwksLoader.getKeyInfo("unknown-kid");
        assertFalse(keyInfo.isPresent(), "Key info should not be present for unknown key ID");
        // Note: KEY_NOT_FOUND events are only incremented during actual token signature validation,
        // not during direct key lookups. This follows the same pattern as other JwksLoader implementations.
    }

    @Test
    @DisplayName("Should return empty for null key ID")
    void shouldReturnEmptyForNullKeyId() {

        Optional<KeyInfo> keyInfo = httpJwksLoader.getKeyInfo(null);
        assertFalse(keyInfo.isPresent(), "Key info should not be present for null key ID");
    }

    @Test
    @DisplayName("Should get key info for test kid")
    void shouldGetKeyInfoForTestKid() {
        // Test getting the specific test key
        Optional<KeyInfo> keyInfo = httpJwksLoader.getKeyInfo(TEST_KID);
        assertTrue(keyInfo.isPresent(), "Key info should be present for test kid");
        assertEquals(TEST_KID, keyInfo.get().keyId(), "Key ID should match test kid");
    }

    @Test
    @DisplayName("Should verify key loading works")
    void shouldVerifyKeyLoadingWorks() {
        // Verify that keys are loaded properly by checking a known key
        Optional<KeyInfo> keyInfo = httpJwksLoader.getKeyInfo(TEST_KID);
        assertTrue(keyInfo.isPresent(), "Key should be loaded successfully");
        assertNotNull(keyInfo.get().key(), "Key object should not be null");
        assertNotNull(keyInfo.get().keyId(), "Key ID should not be null");
    }

    @Test
    @DisplayName("Should verify test key exists")
    void shouldVerifyTestKeyExists() {
        // Verify that the test key is available
        Optional<KeyInfo> keyInfo = httpJwksLoader.getKeyInfo(TEST_KID);
        assertTrue(keyInfo.isPresent(), "Test key should be available");
        assertEquals(TEST_KID, keyInfo.get().keyId(), "Key ID should match expected test ID");
    }

    @Test
    @DisplayName("Should load keys on first access and cache in memory")
    void shouldLoadKeysOnFirstAccess() {

        // First call should load
        Optional<KeyInfo> keyInfo = httpJwksLoader.getKeyInfo(TEST_KID);
        assertTrue(keyInfo.isPresent(), "Key info should be present");
        assertEquals(1, moduleDispatcher.getCallCounter(), "JWKS endpoint should be called once");

        // Subsequent calls should use the already loaded keys without additional HTTP calls
        keyInfo = httpJwksLoader.getKeyInfo(TEST_KID);
        assertTrue(keyInfo.isPresent(), "Key info should still be present");
        assertEquals(1, moduleDispatcher.getCallCounter(), "JWKS endpoint should still be called only once");
    }

    @Test
    @DisplayName("Should handle health checks")
    void shouldHandleHealthChecks() {
        // Initially undefined status
        assertNotNull(httpJwksLoader.getLoaderStatus(), "Status should not be null");

        // After loading, should be healthy
        httpJwksLoader.getKeyInfo(TEST_KID);
        assertEquals(LoaderStatus.OK, httpJwksLoader.getLoaderStatus(), "Should be healthy after successful load");
    }

    @Test
    @ModuleDispatcher
    @DisplayName("Should create new loader with simplified config")
    void shouldCreateNewLoaderWithSimplifiedConfig(URIBuilder uriBuilder) {

        String jwksEndpoint = uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString();
        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .jwksUrl(jwksEndpoint)
                .issuerIdentifier("test-issuer")
                .build();

        HttpJwksLoader customLoader = new HttpJwksLoader(config);
        // Wait for async initialization to complete
        customLoader.initJWKSLoader(securityEventCounter).join();
        assertNotNull(customLoader);

        // Verify it works
        Optional<KeyInfo> keyInfo = customLoader.getKeyInfo(TEST_KID);
        assertTrue(keyInfo.isPresent(), "Key info should be present");
    }


    @Test
    @DisplayName("Should work with multiple loader instances")
    void shouldWorkWithMultipleLoaderInstances(URIBuilder uriBuilder) {
        // First, get a key to ensure keys are loaded
        Optional<KeyInfo> initialKeyInfo = httpJwksLoader.getKeyInfo(TEST_KID);
        assertTrue(initialKeyInfo.isPresent(), "Initial key info should be present");

        // Create a new loader instance with the same configuration
        // This tests that multiple loaders can work independently
        String jwksEndpoint = uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString();
        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .jwksUrl(jwksEndpoint)
                .issuerIdentifier("test-issuer")
                .build();
        try (HttpJwksLoader newLoader = new HttpJwksLoader(config)) {
            // Wait for async initialization to complete
            newLoader.initJWKSLoader(securityEventCounter).join();

            // Verify the new loader works independently
            assertNotNull(newLoader.getLoaderStatus(), "Health check should work for new loader");

            // Both loaders should be functional - test with getKeyInfo
            Optional<KeyInfo> originalLoaderKey = httpJwksLoader.getKeyInfo(TEST_KID);
            Optional<KeyInfo> newLoaderKey = newLoader.getKeyInfo(TEST_KID);
            assertTrue(originalLoaderKey.isPresent(), "Original loader should have the key");
            assertTrue(newLoaderKey.isPresent(), "New loader should be able to retrieve the key");
        }
    }

    @Test
    @DisplayName("Should log info message when JWKS is loaded and parsed")
    void shouldLogInfoMessageWhenJwksIsLoadedAndParsed() {
        // When loading a key, the JWKS is loaded and parsed
        Optional<KeyInfo> keyInfo = httpJwksLoader.getKeyInfo(TEST_KID);

        // Then the key should be found
        assertTrue(keyInfo.isPresent(), "Key info should be present");

        // Verify that JWKS loaded message is logged with issuer information
        LogAsserts.assertLogMessagePresentContaining(
                TestLogLevel.INFO,
                "JWKS loaded successfully for issuer");
    }

    @Test
    @DisplayName("Should log JWKS_LOADED when JWKS is loaded via HTTP")
    void shouldLogJwksLoadedWhenJwksIsLoadedViaHttp() {
        // First access loads keys via HTTP
        Optional<KeyInfo> keyInfo = httpJwksLoader.getKeyInfo(TEST_KID);
        assertTrue(keyInfo.isPresent(), "Key info should be present");

        // Verify JWKS_LOADED was logged (includes issuer information)
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                JWTValidationLogMessages.INFO.JWKS_LOADED.resolveIdentifierString());
    }

    @Test
    @DisplayName("Should log JWKS_KEYS_UPDATED when keys are updated")
    void shouldLogJwksKeysUpdatedWhenKeysAreUpdated() {
        // Load a key to trigger keys update
        Optional<KeyInfo> keyInfo = httpJwksLoader.getKeyInfo(TEST_KID);
        assertTrue(keyInfo.isPresent(), "Key info should be present");

        // JWKS_KEYS_UPDATED is logged when keys are successfully loaded and updated
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                JWTValidationLogMessages.INFO.JWKS_KEYS_UPDATED.resolveIdentifierString());
    }

    @Test
    @DisplayName("Should not support symmetric keys (oct type)")
    void shouldNotSupportSymmetricKeys(URIBuilder uriBuilder) {
        // Save current dispatcher state
        String previousResponse = moduleDispatcher.getCustomResponse();

        try {
            // Setup mock to return symmetric key JWKS
            moduleDispatcher.setCustomResponse("""
                {
                    "keys": [{
                        "kty": "oct",
                        "use": "sig",
                        "kid": "symmetric-key-1",
                        "k": "GawgguFyGrWKav7AX4VKUg",
                        "alg": "HS256"
                    }]
                }
                """);

            String jwksEndpoint = uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString();
            HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                    .jwksUrl(jwksEndpoint)
                    .issuerIdentifier("test-issuer")
                    .build();

            try (HttpJwksLoader loader = new HttpJwksLoader(config)) {
                // Wait for async initialization to complete
                loader.initJWKSLoader(securityEventCounter).join();

                // Try to get the symmetric key - should not be found as oct keys are filtered out
                Optional<KeyInfo> keyInfo = loader.getKeyInfo("symmetric-key-1");
                assertFalse(keyInfo.isPresent(), "Symmetric key should not be supported");

                // The key is simply not found - oct keys are filtered out in JWKSKeyLoader
                // No UNSUPPORTED_JWKS_TYPE error is logged as the filtering happens silently
            }
        } finally {
            // Restore dispatcher state
            moduleDispatcher.setCustomResponse(previousResponse);
        }
    }

    @Nested
    @DisplayName("JWKS Load Failure Tests")
    class JwksLoadFailureTests {
        @Test
        @DisplayName("Should log JWKS_LOAD_FAILED when HTTP connection cannot be established")
        void shouldLogJwksLoadFailedWhenHttpConnectionFails() {
            // Create loader with invalid URL to simulate connection failure
            HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                    .jwksUrl("http://invalid-host-that-does-not-exist:9999/jwks")
                    .issuerIdentifier("test-issuer")
                    .build();

            try (HttpJwksLoader failingLoader = new HttpJwksLoader(config)) {
                // Wait for async initialization to complete (even if it fails)
                failingLoader.initJWKSLoader(securityEventCounter).join();

                // Try to get a key, which should fail
                Optional<KeyInfo> keyInfo = failingLoader.getKeyInfo(TEST_KID);
                assertFalse(keyInfo.isPresent(), "Key info should not be present when connection fails");

                // Verify the appropriate error was logged
                LogAsserts.assertLogMessagePresentContaining(TestLogLevel.ERROR,
                        JWTValidationLogMessages.ERROR.JWKS_LOAD_FAILED.resolveIdentifierString());

                // Also verify the no-cache warning was logged
                LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                        JWTValidationLogMessages.WARN.JWKS_LOAD_FAILED_NO_CACHE.resolveIdentifierString());

                // And the HTTP fetch failure
                LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                        HttpLogMessages.WARN.HTTP_FETCH_FAILED.resolveIdentifierString());
            }
        }

        @Test
        @DisplayName("Should log JWKS_URI_RESOLUTION_FAILED when well-known resolver cannot resolve JWKS URI")
        void shouldLogJwksUriResolutionFailedWhenWellKnownResolverFails(URIBuilder uriBuilder) {
            // Create a well-known config that will fail to resolve JWKS URI
            // Using an invalid well-known endpoint that returns 404
            String invalidWellKnownUrl = uriBuilder.addPathSegment("invalid-well-known").buildAsString();

            HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                    .wellKnownUrl(invalidWellKnownUrl)
                    .build();

            try (HttpJwksLoader failingLoader = new HttpJwksLoader(config)) {
                // Wait for async initialization to complete (even if it fails)
                failingLoader.initJWKSLoader(securityEventCounter).join();

                // Try to get a key, which should fail because JWKS URI cannot be resolved
                Optional<KeyInfo> keyInfo = failingLoader.getKeyInfo(TEST_KID);
                assertFalse(keyInfo.isPresent(), "Key info should not be present when JWKS URI resolution fails");

                // Verify JWKS_URI_RESOLUTION_FAILED was logged
                LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                        JWTValidationLogMessages.WARN.JWKS_URI_RESOLUTION_FAILED.resolveIdentifierString());
            }
        }
    }

    @Nested
    @DisplayName("Async Initialization Tests")
    class AsyncInitializationTests {

        @Getter
        private final JwksResolveDispatcher moduleDispatcher = new JwksResolveDispatcher();

        @BeforeEach
        void setUp() {
            moduleDispatcher.setCallCounter(0);
            moduleDispatcher.returnDefault();
        }

        @Test
        @DisplayName("Constructor should not block or perform I/O operations")
        void constructorShouldNotBlockOrPerformIO(URIBuilder uriBuilder) {
            String jwksEndpoint = uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString();
            HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                    .jwksUrl(jwksEndpoint)
                    .issuerIdentifier("test-issuer")
                    .build();

            // Measure constructor execution time
            long startTime = System.nanoTime();
            try (HttpJwksLoader loader = new HttpJwksLoader(config)) {
                long constructorDurationNanos = System.nanoTime() - startTime;

                // Constructor should complete in < 10ms (no I/O operations)
                long maxAllowedNanos = TimeUnit.MILLISECONDS.toNanos(10);
                assertTrue(constructorDurationNanos < maxAllowedNanos,
                        "Constructor took %d ns (%.2f ms), should be < %d ns (10 ms)".formatted(
                                constructorDurationNanos,
                                constructorDurationNanos / 1_000_000.0,
                                maxAllowedNanos));

                // Status should be UNDEFINED immediately after construction (no loading yet)
                assertEquals(LoaderStatus.UNDEFINED, loader.getLoaderStatus(),
                        "Status should be UNDEFINED immediately after construction");

                // Verify no HTTP calls were made during construction
                assertEquals(0, moduleDispatcher.getCallCounter(),
                        "No HTTP calls should be made during construction");
            }
        }

        @Test
        @DisplayName("initJWKSLoader should return non-completed CompletableFuture")
        void initJWKSLoaderShouldReturnAsyncCompletableFuture(URIBuilder uriBuilder) {
            String jwksEndpoint = uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString();
            HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                    .jwksUrl(jwksEndpoint)
                    .issuerIdentifier("test-issuer")
                    .build();

            try (HttpJwksLoader loader = new HttpJwksLoader(config)) {
                SecurityEventCounter counter = new SecurityEventCounter();

                // Call initJWKSLoader - should return CompletableFuture
                CompletableFuture<LoaderStatus> initFuture = loader.initJWKSLoader(counter);

                // Verify it returns a CompletableFuture
                assertNotNull(initFuture, "initJWKSLoader should return a CompletableFuture");

                // The future should complete eventually with OK status
                LoaderStatus finalStatus = initFuture.join();
                assertEquals(LoaderStatus.OK, finalStatus, "Initialization should complete with OK status");

                // After completion, loader should be in OK state
                assertEquals(LoaderStatus.OK, loader.getLoaderStatus(),
                        "Loader status should be OK after successful initialization");
            }
        }

        @Test
        @DisplayName("Status should transition atomically during async initialization")
        void statusShouldTransitionAtomicallyDuringAsyncInit(URIBuilder uriBuilder) {
            String jwksEndpoint = uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString();
            HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                    .jwksUrl(jwksEndpoint)
                    .issuerIdentifier("test-issuer")
                    .build();

            try (HttpJwksLoader loader = new HttpJwksLoader(config)) {
                SecurityEventCounter counter = new SecurityEventCounter();

                // Initial status should be UNDEFINED
                assertEquals(LoaderStatus.UNDEFINED, loader.getLoaderStatus(),
                        "Initial status should be UNDEFINED");

                // Start async initialization
                CompletableFuture<LoaderStatus> initFuture = loader.initJWKSLoader(counter);

                // Wait for status to transition to LOADING, then to OK
                Awaitility.await()
                        .atMost(Duration.ofSeconds(5))
                        .until(() -> loader.getLoaderStatus() == LoaderStatus.OK);

                // Verify final status
                assertEquals(LoaderStatus.OK, loader.getLoaderStatus(),
                        "Final status should be OK");

                // Verify future completed with OK
                assertTrue(initFuture.isDone(), "Future should be completed");
                assertEquals(LoaderStatus.OK, initFuture.join(), "Future should complete with OK");
            }
        }

        @Test
        @DisplayName("Multiple concurrent initJWKSLoader calls should be handled safely")
        void multipleConcurrentInitCallsShouldBeSafe(URIBuilder uriBuilder) {
            String jwksEndpoint = uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString();
            HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                    .jwksUrl(jwksEndpoint)
                    .issuerIdentifier("test-issuer")
                    .build();

            try (HttpJwksLoader loader = new HttpJwksLoader(config)) {
                SecurityEventCounter counter = new SecurityEventCounter();

                // Make multiple concurrent calls to initJWKSLoader
                CompletableFuture<LoaderStatus> future1 = loader.initJWKSLoader(counter);
                CompletableFuture<LoaderStatus> future2 = loader.initJWKSLoader(counter);
                CompletableFuture<LoaderStatus> future3 = loader.initJWKSLoader(counter);

                // All futures should complete with OK
                assertEquals(LoaderStatus.OK, future1.join(), "First init should complete with OK");
                assertEquals(LoaderStatus.OK, future2.join(), "Second init should complete with OK");
                assertEquals(LoaderStatus.OK, future3.join(), "Third init should complete with OK");

                // Final loader status should be OK
                assertEquals(LoaderStatus.OK, loader.getLoaderStatus(),
                        "Loader status should be OK after concurrent initialization");

                // Keys should be available
                Optional<KeyInfo> keyInfo = loader.getKeyInfo(TEST_KID);
                assertTrue(keyInfo.isPresent(), "Keys should be available after initialization");
            }
        }
    }

    @Nested
    @DisplayName("Well-Known Discovery Async Tests")
    class WellKnownAsyncTests {

        @Test
        @DisplayName("Constructor should not perform well-known discovery")
        void constructorShouldNotPerformWellKnownDiscovery() {
            // Create a well-known configuration with invalid URL to test constructor behavior
            String invalidWellKnownUrl = "https://invalid-host.example.com/.well-known/openid_configuration";

            HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                    .wellKnownUrl(invalidWellKnownUrl)
                    .build();

            // Measure constructor time
            long startTime = System.nanoTime();
            try (HttpJwksLoader loader = new HttpJwksLoader(config)) {
                long constructorDurationNanos = System.nanoTime() - startTime;

                // Constructor should be fast (< 10ms) even with well-known URL
                assertTrue(constructorDurationNanos < TimeUnit.MILLISECONDS.toNanos(10),
                        "Constructor should complete quickly without well-known discovery");

                assertEquals(LoaderStatus.UNDEFINED, loader.getLoaderStatus(),
                        "Status should remain UNDEFINED until initialization");
            }
        }

        @Test
        @DisplayName("Well-known discovery failure should be handled in async context")
        void wellKnownDiscoveryFailureShouldBeHandledAsync() {
            // Create configuration with invalid well-known URL
            String invalidWellKnownUrl = "https://invalid-host.example.com/.well-known/openid_configuration";

            HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                    .wellKnownUrl(invalidWellKnownUrl)
                    .build();

            try (HttpJwksLoader loader = new HttpJwksLoader(config)) {
                SecurityEventCounter counter = new SecurityEventCounter();

                // Constructor should still be fast even with invalid well-known URL
                long startTime = System.nanoTime();
                try (HttpJwksLoader ignored = new HttpJwksLoader(config)) {
                    long constructorDuration = System.nanoTime() - startTime;

                    assertTrue(constructorDuration < TimeUnit.MILLISECONDS.toNanos(10),
                            "Constructor should be fast even with invalid well-known configuration");
                }

                // Async initialization should handle the failure
                CompletableFuture<LoaderStatus> initFuture = loader.initJWKSLoader(counter);

                // Wait for completion - should fail gracefully
                LoaderStatus status = initFuture.join();
                assertEquals(LoaderStatus.ERROR, status, "Initialization should fail with well-known discovery error");
                assertEquals(LoaderStatus.ERROR, loader.getLoaderStatus(), "Loader should be in ERROR status");

                // Verify appropriate error logging
                LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                        JWTValidationLogMessages.WARN.JWKS_URI_RESOLUTION_FAILED.resolveIdentifierString());
            }
        }
    }

    @Nested
    @DisplayName("Lock-Free Status Check Tests")
    class LockFreeStatusTests {

        @Getter
        private final JwksResolveDispatcher moduleDispatcher = new JwksResolveDispatcher();

        @BeforeEach
        void setUp() {
            moduleDispatcher.setCallCounter(0);
            moduleDispatcher.returnDefault();
        }

        @Test
        @DisplayName("getLoaderStatus should be lock-free under high contention")
        void getLoaderStatusShouldBeLockFreeUnderHighContention(URIBuilder uriBuilder) throws InterruptedException {
            String jwksEndpoint = uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString();
            HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                    .jwksUrl(jwksEndpoint)
                    .issuerIdentifier("lock-free-test-issuer")
                    .build();

            try (HttpJwksLoader loader = new HttpJwksLoader(config)) {
                SecurityEventCounter counter = new SecurityEventCounter();

                // Start async initialization to get loader into active state
                CompletableFuture<LoaderStatus> initFuture = loader.initJWKSLoader(counter);

                int threadCount = 100;
                try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
                    CyclicBarrier barrier = new CyclicBarrier(threadCount);
                    CountDownLatch endLatch = new CountDownLatch(threadCount);
                    AtomicInteger successCount = new AtomicInteger(0);
                    AtomicReference<BrokenBarrierException> barrierException = new AtomicReference<>();
                    AtomicReference<InterruptedException> interruptedException = new AtomicReference<>();

                    // Launch 100 threads that will hammer getLoaderStatus() simultaneously
                    for (int i = 0; i < threadCount; i++) {
                        executor.submit(() -> {
                            try {
                                // Synchronize all threads to start at exactly the same time
                                barrier.await();

                                // Each thread calls getLoaderStatus() multiple times rapidly
                                for (int j = 0; j < 50; j++) {
                                    LoaderStatus status = loader.getLoaderStatus();
                                    assertNotNull(status, "Status should never be null");
                                    // Status should be one of the valid enum values
                                    assertTrue(
                                            status == LoaderStatus.UNDEFINED ||
                                                    status == LoaderStatus.LOADING ||
                                                    status == LoaderStatus.OK ||
                                                    status == LoaderStatus.ERROR,
                                            "Status should be a valid LoaderStatus value: " + status
                                    );
                                }
                                successCount.incrementAndGet();

                            } catch (BrokenBarrierException e) {
                                barrierException.compareAndSet(null, e);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                interruptedException.compareAndSet(null, e);
                            } finally {
                                endLatch.countDown();
                            }
                        });
                    }

                    // Wait for all threads to complete
                    boolean completed = endLatch.await(10, TimeUnit.SECONDS);
                    executor.shutdown();

                    assertTrue(completed, "All threads should complete within timeout");
                    assertNull(barrierException.get(), "No barrier exceptions should occur during concurrent access");
                    assertNull(interruptedException.get(), "No interruption exceptions should occur during concurrent access");
                    assertEquals(threadCount, successCount.get(), "All threads should successfully read status");

                    // Ensure initialization completes properly
                    LoaderStatus finalStatus = initFuture.join();
                    assertTrue(finalStatus == LoaderStatus.OK || finalStatus == LoaderStatus.ERROR,
                            "Initialization should complete with OK or ERROR status");
                } // Close try-with-resources for executor
            }
        }

        @Test
        @DisplayName("Status transitions should be atomic and consistent")
        void statusTransitionsShouldBeAtomicAndConsistent(URIBuilder uriBuilder) throws InterruptedException {
            String jwksEndpoint = uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString();
            HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                    .jwksUrl(jwksEndpoint)
                    .issuerIdentifier("atomic-transition-test-issuer")
                    .build();

            try (HttpJwksLoader loader = new HttpJwksLoader(config)) {
                SecurityEventCounter counter = new SecurityEventCounter();

                // Initial status should be UNDEFINED
                assertEquals(LoaderStatus.UNDEFINED, loader.getLoaderStatus(),
                        "Initial status should be UNDEFINED");

                int observerThreadCount = 50;
                try (ExecutorService observerExecutor = Executors.newFixedThreadPool(observerThreadCount)) {
                    CountDownLatch observerLatch = new CountDownLatch(observerThreadCount);
                    AtomicInteger undefinedObservations = new AtomicInteger(0);
                    AtomicInteger loadingObservations = new AtomicInteger(0);
                    AtomicInteger okObservations = new AtomicInteger(0);
                    AtomicInteger errorObservations = new AtomicInteger(0);
                    AtomicInteger invalidTransitions = new AtomicInteger(0);

                    // Start multiple threads observing status transitions
                    for (int i = 0; i < observerThreadCount; i++) {
                        observerExecutor.submit(() -> {
                            try {
                                LoaderStatus previousStatus = null;

                                // Observe status changes for up to 5 seconds
                                long endTime = System.currentTimeMillis() + 5000;
                                while (System.currentTimeMillis() < endTime) {
                                    LoaderStatus currentStatus = loader.getLoaderStatus();

                                    // Count observations of each status
                                    switch (currentStatus) {
                                        case UNDEFINED -> undefinedObservations.incrementAndGet();
                                        case LOADING -> loadingObservations.incrementAndGet();
                                        case OK -> okObservations.incrementAndGet();
                                        case ERROR -> errorObservations.incrementAndGet();
                                    }

                                    // Check for invalid transitions
                                    // Valid transitions: UNDEFINED -> LOADING, LOADING -> OK/ERROR
                                    // Invalid: OK -> anything, ERROR -> anything, LOADING -> UNDEFINED
                                    if ((previousStatus == LoaderStatus.OK && currentStatus != LoaderStatus.OK) ||
                                            (previousStatus == LoaderStatus.ERROR && currentStatus != LoaderStatus.ERROR) ||
                                            (previousStatus == LoaderStatus.LOADING && currentStatus == LoaderStatus.UNDEFINED)) {
                                        invalidTransitions.incrementAndGet();
                                    }

                                    previousStatus = currentStatus;

                                    // Brief pause to allow other threads to observe
                                    Awaitility.await().pollDelay(Duration.ofMillis(1)).until(() -> true);
                                }
                            } finally {
                                observerLatch.countDown();
                            }
                        });
                    }

                    // Start async initialization after observers are running
                    Awaitility.await().pollDelay(Duration.ofMillis(100)).until(() -> true); // Give observers time to start
                    CompletableFuture<LoaderStatus> initFuture = loader.initJWKSLoader(counter);

                    // Wait for initialization to complete
                    LoaderStatus finalStatus = initFuture.join();

                    // Wait for all observer threads to complete
                    boolean observersCompleted = observerLatch.await(10, TimeUnit.SECONDS);

                    assertTrue(observersCompleted, "All observer threads should complete");
                    assertEquals(0, invalidTransitions.get(),
                            "No invalid status transitions should be observed");

                    // Verify expected transition pattern
                    assertTrue(undefinedObservations.get() > 0,
                            "Should observe UNDEFINED status");
                    assertTrue(okObservations.get() > 0 || errorObservations.get() > 0,
                            "Should observe final status (OK or ERROR)");
                    assertTrue(finalStatus == LoaderStatus.OK || finalStatus == LoaderStatus.ERROR,
                            "Final status should be OK or ERROR");

                    // If successful, should have observed LOADING state
                    if (finalStatus == LoaderStatus.OK) {
                        assertTrue(loadingObservations.get() > 0,
                                "Should observe LOADING status during successful initialization");
                    }
                } // Close try-with-resources for observerExecutor
            }
        }

        @Test
        @DisplayName("Concurrent status checks during multiple initializations should be safe")
        void concurrentStatusChecksDuringMultipleInitializationsShouldBeSafe(URIBuilder uriBuilder) throws InterruptedException {
            String jwksEndpoint = uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString();
            HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                    .jwksUrl(jwksEndpoint)
                    .issuerIdentifier("multi-init-test-issuer")
                    .build();

            try (HttpJwksLoader loader = new HttpJwksLoader(config)) {
                SecurityEventCounter counter = new SecurityEventCounter();

                int initThreadCount = 10;
                int statusCheckThreadCount = 50;
                try (ExecutorService executor = Executors.newFixedThreadPool(initThreadCount + statusCheckThreadCount)) {
                    CountDownLatch startLatch = new CountDownLatch(1);
                    CountDownLatch endLatch = new CountDownLatch(initThreadCount + statusCheckThreadCount);
                    AtomicInteger statusCheckSuccesses = new AtomicInteger(0);
                    AtomicInteger initSuccesses = new AtomicInteger(0);
                    AtomicReference<InterruptedException> initInterruptedException = new AtomicReference<>();

                    // Launch multiple threads calling initJWKSLoader concurrently
                    for (int i = 0; i < initThreadCount; i++) {
                        executor.submit(() -> {
                            try {
                                startLatch.await();

                                CompletableFuture<LoaderStatus> future = loader.initJWKSLoader(counter);
                                LoaderStatus result = future.join();
                                assertTrue(result == LoaderStatus.OK || result == LoaderStatus.ERROR,
                                        "Init should complete with OK or ERROR");
                                initSuccesses.incrementAndGet();

                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                initInterruptedException.compareAndSet(null, e);
                            } finally {
                                endLatch.countDown();
                            }
                        });
                    }

                    // Launch many threads continuously checking status
                    for (int i = 0; i < statusCheckThreadCount; i++) {
                        executor.submit(() -> {
                            try {
                                startLatch.await();

                                // Check status repeatedly for 3 seconds
                                long endTime = System.currentTimeMillis() + 3000;
                                while (System.currentTimeMillis() < endTime) {
                                    LoaderStatus status = loader.getLoaderStatus();
                                    assertNotNull(status, "Status should never be null");

                                    // Use Awaitility instead of Thread.sleep for better testing
                                    Awaitility.await().pollDelay(Duration.ofMillis(1)).until(() -> true);
                                }
                                statusCheckSuccesses.incrementAndGet();

                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            } finally {
                                endLatch.countDown();
                            }
                        });
                    }

                    // Start all threads simultaneously
                    startLatch.countDown();

                    // Wait for completion
                    boolean completed = endLatch.await(15, TimeUnit.SECONDS);

                    assertTrue(completed, "All threads should complete");
                    assertNull(initInterruptedException.get(), "No interruption exceptions should occur");
                    assertTrue(initSuccesses.get() > 0, "Some initializations should succeed");
                    assertEquals(statusCheckThreadCount, statusCheckSuccesses.get(),
                            "All status check threads should complete successfully");
                } // Close try-with-resources for executor
            }
        }
    }
}
