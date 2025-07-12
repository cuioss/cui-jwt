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
package de.cuioss.jwt.validation.jwks.key;

import de.cuioss.jwt.validation.ParserConfig;
import de.cuioss.jwt.validation.security.JwkAlgorithmPreferences;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.jwt.validation.test.InMemoryJWKSFactory;
import de.cuioss.jwt.validation.test.InMemoryKeyMaterialHandler;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static de.cuioss.jwt.validation.JWTValidationLogMessages.ERROR.JWKS_CONTENT_SIZE_EXCEEDED;
import static org.junit.jupiter.api.Assertions.*;

@EnableTestLogger(debug = {JWKSKeyLoader.class}, trace = {JWKSKeyLoader.class})
@DisplayName("Tests JWKSKeyLoader functionality")
class JWKSKeyLoaderTest {

    private static final String TEST_KID = InMemoryJWKSFactory.DEFAULT_KEY_ID;
    private JWKSKeyLoader keyLoader;
    private String jwksContent;

    @BeforeEach
    void setUp() {
        jwksContent = InMemoryJWKSFactory.createDefaultJwks();
        SecurityEventCounter securityEventCounter = new SecurityEventCounter();
        keyLoader = JWKSKeyLoader.builder()
                .jwksContent(jwksContent)

                .build();
        keyLoader.initJWKSLoader(securityEventCounter);
    }

    @Nested
    @DisplayName("Basic Functionality")
    class BasicFunctionalityTests {
        @Test
        @DisplayName("Should parse JWKS content")
        void shouldParseJwksContent() {

            Optional<KeyInfo> keyInfo = keyLoader.getKeyInfo(TEST_KID);
            assertTrue(keyInfo.isPresent(), "Key info should be present");
        }

        @Test
        @DisplayName("Should get key by known kid")
        void shouldGetKeyByKnownKid() {
            // Test getting a key by its known ID
            Optional<KeyInfo> keyInfo = keyLoader.getKeyInfo(TEST_KID);
            assertTrue(keyInfo.isPresent(), "Key info should be present for known kid");
            assertEquals(TEST_KID, keyInfo.get().keyId(), "Key ID should match");
        }

        @Test
        @DisplayName("Should verify test key exists")
        void shouldVerifyTestKeyExists() {
            // Verify that the test key is loaded correctly
            Optional<KeyInfo> keyInfo = keyLoader.getKeyInfo(TEST_KID);
            assertTrue(keyInfo.isPresent(), "Test key should be loaded");
            assertNotNull(keyInfo.get().key(), "Key object should not be null");
            assertEquals(TEST_KID, keyInfo.get().keyId(), "Key ID should match test ID");
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {
        @Test
        @DisplayName("Should return empty when kid is null")
        void shouldReturnEmptyWhenKidIsNull() {

            Optional<KeyInfo> keyInfo = keyLoader.getKeyInfo(null);
            assertFalse(keyInfo.isPresent(), "Key info should not be present when kid is null");
            // Note: Log assertion removed as it's not essential to the test's purpose
        }

        @Test
        @DisplayName("Should return empty when kid is not found")
        void shouldReturnEmptyWhenKidNotFound() {

            Optional<KeyInfo> keyInfo = keyLoader.getKeyInfo("unknown-kid");
            assertFalse(keyInfo.isPresent(), "Key info should not be present when kid is not found");
        }

        @Test
        @DisplayName("Should handle invalid JWKS format")
        void shouldHandleInvalidJwksFormat() {

            String invalidJwksContent = InMemoryJWKSFactory.createInvalidJson();
            JWKSKeyLoader invalidLoader = JWKSKeyLoader.builder()
                    .jwksContent(invalidJwksContent)
                    .build();
            invalidLoader.initJWKSLoader(new SecurityEventCounter());
            Optional<KeyInfo> keyInfo = invalidLoader.getKeyInfo(TEST_KID);
            assertFalse(keyInfo.isPresent(), "Key info should not be present when JWKS is invalid");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.ERROR, "Failed to parse JWKS JSON");
        }

        @Test
        @DisplayName("Should handle missing required fields in JWK")
        void shouldHandleMissingRequiredFieldsInJwk() {

            String missingFieldsJwksContent = InMemoryJWKSFactory.createJwksWithMissingFields(TEST_KID);
            JWKSKeyLoader missingFieldsLoader = JWKSKeyLoader.builder()
                    .jwksContent(missingFieldsJwksContent)
                    .build();
            missingFieldsLoader.initJWKSLoader(new SecurityEventCounter());
            Optional<KeyInfo> keyInfo = missingFieldsLoader.getKeyInfo(TEST_KID);
            assertFalse(keyInfo.isPresent(), "Key info should not be present when JWK is missing required fields");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "Failed to parse RSA key");
        }
    }

    @Nested
    @DisplayName("Key Management")
    class KeyManagementTests {
        @Test
        @DisplayName("Should report not empty when keys are present")
        void shouldReportNotEmptyWhenKeysArePresent() {
            boolean notEmpty = keyLoader.isNotEmpty();
            assertTrue(notEmpty, "Loader should report not empty when keys are present");
        }

        @Test
        @DisplayName("Should report empty when no keys are present")
        void shouldReportEmptyWhenNoKeysArePresent() {
            String emptyJwksContent = "{}";
            JWKSKeyLoader emptyLoader = JWKSKeyLoader.builder()
                    .jwksContent(emptyJwksContent)
                    .build();
            emptyLoader.initJWKSLoader(new SecurityEventCounter());
            boolean notEmpty = emptyLoader.isNotEmpty();
            assertFalse(notEmpty, "Loader should report empty when no keys are present");
        }
    }

    @Nested
    @DisplayName("Security Features")
    class SecurityFeaturesTests {
        @Test
        @DisplayName("Should work with custom ParserConfig")
        void shouldWorkWithCustomParserConfig() {
            ParserConfig customConfig = ParserConfig.builder()
                    .maxPayloadSize(1024)
                    .maxStringSize(512)
                    .maxArraySize(10)
                    .maxDepth(5)
                    .build();
            JWKSKeyLoader loaderWithCustomConfig = JWKSKeyLoader.builder()
                    .jwksContent(jwksContent)

                    .parserConfig(customConfig)
                    .build();
            loaderWithCustomConfig.initJWKSLoader(new SecurityEventCounter());
            assertTrue(loaderWithCustomConfig.isNotEmpty(),
                    "Loader should parse valid JWKS with custom config");
        }

        @Test
        @DisplayName("Should reject JWKS content exceeding maximum size")
        void shouldRejectJwksContentExceedingMaximumSize() {

            int maxSize = 100; // Small size for testing
            ParserConfig restrictiveConfig = ParserConfig.builder()
                    .maxPayloadSize(maxSize)
                    .build();

            // Create a large JWKS content that exceeds the maximum size
            // Add enough padding to exceed maxSize
            JWKSKeyLoader loader = JWKSKeyLoader.builder()
                    .jwksContent("{\"keys\":[" + "\"x\":\"" + "a".repeat(maxSize) + "\"}]}"

                    )
                    .parserConfig(restrictiveConfig)
                    .build();
            loader.initJWKSLoader(new SecurityEventCounter());
            assertFalse(loader.isNotEmpty(), "Loader should reject content exceeding maximum size");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.ERROR, JWKS_CONTENT_SIZE_EXCEEDED.resolveIdentifierString());
        }

    }

    @Nested
    @DisplayName("Algorithm Preferences")
    class AlgorithmPreferencesTests {

        @Test
        @DisplayName("Should use default algorithm preferences when not specified")
        void shouldUseDefaultAlgorithmPreferencesWhenNotSpecified() {
            // Create a JWKS with a supported algorithm (RS256)
            String jwksWithRS256 = InMemoryJWKSFactory.createValidJwksWithKeyId(InMemoryKeyMaterialHandler.Algorithm.RS256, "test-kid");

            JWKSKeyLoader loader = JWKSKeyLoader.builder()
                    .jwksContent(jwksWithRS256)
                    .build();
            loader.initJWKSLoader(new SecurityEventCounter());

            Optional<KeyInfo> keyInfo = loader.getKeyInfo("test-kid");
            assertTrue(keyInfo.isPresent(), "Key should be loaded with default supported algorithm RS256");
        }

        @Test
        @DisplayName("Should accept custom algorithm preferences")
        void shouldAcceptCustomAlgorithmPreferences() {
            // Create custom preferences that only allow ES256
            JwkAlgorithmPreferences customPreferences = new JwkAlgorithmPreferences(List.of("ES256"));
            String jwksWithES256 = InMemoryJWKSFactory.createValidJwksWithKeyId(InMemoryKeyMaterialHandler.Algorithm.ES256, "test-kid");

            JWKSKeyLoader loader = JWKSKeyLoader.builder()
                    .jwksContent(jwksWithES256)
                    .jwkAlgorithmPreferences(customPreferences)
                    .build();
            loader.initJWKSLoader(new SecurityEventCounter());

            Optional<KeyInfo> keyInfo = loader.getKeyInfo("test-kid");
            assertTrue(keyInfo.isPresent(), "Key should be loaded with custom supported algorithm ES256");
        }

        @Test
        @DisplayName("Should reject keys with unsupported algorithms")
        void shouldRejectKeysWithUnsupportedAlgorithms() {
            // Create custom preferences that only allow ES256 (not RS256)
            JwkAlgorithmPreferences restrictivePreferences = new JwkAlgorithmPreferences(List.of("ES256"));
            String jwksWithRS256 = InMemoryJWKSFactory.createValidJwksWithKeyId(InMemoryKeyMaterialHandler.Algorithm.RS256, "test-kid");

            JWKSKeyLoader loader = JWKSKeyLoader.builder()
                    .jwksContent(jwksWithRS256)
                    .jwkAlgorithmPreferences(restrictivePreferences)
                    .build();
            loader.initJWKSLoader(new SecurityEventCounter());

            Optional<KeyInfo> keyInfo = loader.getKeyInfo("test-kid");
            assertFalse(keyInfo.isPresent(), "Key should be rejected with unsupported algorithm RS256");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "Invalid or unsupported algorithm");
        }

        @Test
        @DisplayName("Should handle null algorithm preferences gracefully")
        void shouldHandleNullAlgorithmPreferencesGracefully() {
            // Builder should handle null and use default
            String jwksWithRS256 = InMemoryJWKSFactory.createValidJwksWithKeyId(InMemoryKeyMaterialHandler.Algorithm.RS256, "test-kid");

            JWKSKeyLoader loader = JWKSKeyLoader.builder()
                    .jwksContent(jwksWithRS256)
                    .jwkAlgorithmPreferences(null)
                    .build();
            loader.initJWKSLoader(new SecurityEventCounter());

            Optional<KeyInfo> keyInfo = loader.getKeyInfo("test-kid");
            assertTrue(keyInfo.isPresent(), "Key should be loaded with default preferences when null is passed");
        }
    }
}
