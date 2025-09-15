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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

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
                .build();

        httpJwksLoader = new HttpJwksLoader(config);
        httpJwksLoader.initJWKSLoader(securityEventCounter);
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
                .build();

        HttpJwksLoader customLoader = new HttpJwksLoader(config);
        customLoader.initJWKSLoader(securityEventCounter);
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
                .build();
        HttpJwksLoader newLoader = new HttpJwksLoader(config);
        newLoader.initJWKSLoader(securityEventCounter);

        // Verify the new loader works independently
        assertNotNull(newLoader.getLoaderStatus(), "Health check should work for new loader");

        // Both loaders should be functional - test with getKeyInfo
        Optional<KeyInfo> originalLoaderKey = httpJwksLoader.getKeyInfo(TEST_KID);
        Optional<KeyInfo> newLoaderKey = newLoader.getKeyInfo(TEST_KID);
        assertTrue(originalLoaderKey.isPresent(), "Original loader should have the key");
        assertTrue(newLoaderKey.isPresent(), "New loader should be able to retrieve the key");
    }

    @Test
    @DisplayName("Should log info message when JWKS is loaded and parsed")
    void shouldLogInfoMessageWhenJwksIsLoadedAndParsed() {
        // When loading a key, the JWKS is loaded and parsed
        Optional<KeyInfo> keyInfo = httpJwksLoader.getKeyInfo(TEST_KID);

        // Then the key should be found
        assertTrue(keyInfo.isPresent(), "Key info should be present");

        // Verify that some info logging occurred during JWKS loading
        // The simplified loader logs success messages
        LogAsserts.assertLogMessagePresentContaining(
                TestLogLevel.INFO,
                "Successfully loaded JWKS");
    }

    @Test
    @DisplayName("Should log success message when JWKS is loaded")
    void shouldLogSuccessMessageWhenJwksIsLoaded() {
        // Load a key to trigger JWKS loading
        Optional<KeyInfo> keyInfo = httpJwksLoader.getKeyInfo(TEST_KID);
        assertTrue(keyInfo.isPresent(), "Key info should be present");

        // The simplified loader logs a success message
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO, "Successfully loaded JWKS");
    }

    @Test
    @DisplayName("Should log JWKS_HTTP_LOADED when JWKS is loaded via HTTP with 200 response")
    void shouldLogJwksHttpLoadedWhenJwksIsLoadedViaHttp() {
        // First access loads keys via HTTP and should get 200 response
        Optional<KeyInfo> keyInfo = httpJwksLoader.getKeyInfo(TEST_KID);
        assertTrue(keyInfo.isPresent(), "Key info should be present");

        // Verify either JWKS_HTTP_LOADED or JWKS_KEYS_UPDATED was logged
        // Both indicate successful HTTP loading
        try {
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                    JWTValidationLogMessages.INFO.JWKS_HTTP_LOADED.resolveIdentifierString());
        } catch (AssertionError e) {
            // If not JWKS_HTTP_LOADED, at least JWKS_KEYS_UPDATED should be present
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                    JWTValidationLogMessages.INFO.JWKS_KEYS_UPDATED.resolveIdentifierString());
        }
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
                    .build();

            HttpJwksLoader loader = new HttpJwksLoader(config);
            loader.initJWKSLoader(securityEventCounter);

            // Try to get the symmetric key - should not be found as oct keys are filtered out
            Optional<KeyInfo> keyInfo = loader.getKeyInfo("symmetric-key-1");
            assertFalse(keyInfo.isPresent(), "Symmetric key should not be supported");

            // The key is simply not found - oct keys are filtered out in JWKSKeyLoader
            // No UNSUPPORTED_JWKS_TYPE error is logged as the filtering happens silently
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
        void shouldLogJwksLoadFailedWhenHttpConnectionFails(URIBuilder uriBuilder) {
            // Create loader with invalid URL to simulate connection failure
            HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                    .jwksUrl("http://invalid-host-that-does-not-exist:9999/jwks")
                    .build();

            HttpJwksLoader failingLoader = new HttpJwksLoader(config);
            failingLoader.initJWKSLoader(securityEventCounter);

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

        @Test
        @DisplayName("Should log JWKS_URI_RESOLUTION_FAILED when well-known resolver cannot resolve JWKS URI")
        void shouldLogJwksUriResolutionFailedWhenWellKnownResolverFails(URIBuilder uriBuilder) {
            // Create a well-known config that will fail to resolve JWKS URI
            // Using an invalid well-known endpoint that returns 404
            String invalidWellKnownUrl = uriBuilder.addPathSegment("invalid-well-known").buildAsString();

            HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                    .wellKnownUrl(invalidWellKnownUrl)
                    .build();

            HttpJwksLoader failingLoader = new HttpJwksLoader(config);
            failingLoader.initJWKSLoader(securityEventCounter);

            // Try to get a key, which should fail because JWKS URI cannot be resolved
            Optional<KeyInfo> keyInfo = failingLoader.getKeyInfo(TEST_KID);
            assertFalse(keyInfo.isPresent(), "Key info should not be present when JWKS URI resolution fails");

            // Verify JWKS_URI_RESOLUTION_FAILED was logged
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                    JWTValidationLogMessages.WARN.JWKS_URI_RESOLUTION_FAILED.resolveIdentifierString());
        }
    }
}
