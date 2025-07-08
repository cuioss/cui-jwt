/**
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
package de.cuioss.jwt.validation;

import de.cuioss.jwt.validation.domain.claim.mapper.IdentityMapper;
import de.cuioss.jwt.validation.jwks.JwksLoader;
import de.cuioss.jwt.validation.jwks.http.HttpJwksLoaderConfig;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.jwt.validation.security.SignatureAlgorithmPreferences;
import de.cuioss.test.valueobjects.junit5.contracts.ShouldImplementEqualsAndHashCode;
import de.cuioss.test.valueobjects.junit5.contracts.ShouldImplementToString;
import de.cuioss.tools.logging.CuiLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link IssuerConfig} verifying value object contracts.
 * <p>
 * Supports requirement CUI-JWT-1.2: Multi-Issuer Support.
 *
 * @author Oliver Wolff
 * @see <a href="https://github.com/cuioss/cui-jwt/tree/main/doc/specification/technical-components.adoc#multi-issuer">Multi-Issuer Specification</a>
 */
@DisplayName("Tests for IssuerConfig")
class IssuerConfigTest implements ShouldImplementToString<IssuerConfig>, ShouldImplementEqualsAndHashCode<IssuerConfig> {

    private static final CuiLogger LOGGER = new CuiLogger(IssuerConfigTest.class);
    private static final String TEST_ISSUER = "https://test-issuer.example.com";
    private static final String TEST_AUDIENCE = "test-audience";
    private static final String TEST_CLIENT_ID = "test-client-id";
    private static final String TEST_JWKS_URL = "https://test-issuer.example.com/.well-known/jwks.json";
    private static final String TEST_JWKS_CONTENT = "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"test-key-id\"}]}";

    @Override
    public IssuerConfig getUnderTest() {
        return IssuerConfig.builder()
                .issuerIdentifier(TEST_ISSUER)
                .jwksContent(TEST_JWKS_CONTENT)
                .build();
    }

    @Nested
    @DisplayName("Tests for builder and configuration")
    class BuilderTests {

        @Test
        @DisplayName("Should build with minimal configuration (requires issuerIdentifier)")
        void shouldBuildWithMinimalConfig() {
            // When
            var config = IssuerConfig.builder()
                    .issuerIdentifier(TEST_ISSUER)  // Required for in-memory JWKS
                    .jwksContent(TEST_JWKS_CONTENT)
                    .build();

            // Then
            assertNotNull(config.getJwksLoader());
            assertEquals(TEST_ISSUER, config.getIssuerIdentifier());
            assertTrue(config.getExpectedAudience().isEmpty());
            assertTrue(config.getExpectedClientId().isEmpty());
            assertNotNull(config.getAlgorithmPreferences());
        }

        @Test
        @DisplayName("Should build with complete configuration")
        void shouldBuildWithCompleteConfig() {
            // Given
            var audience = TEST_AUDIENCE;
            var clientId = TEST_CLIENT_ID;
            var algorithmPreferences = new SignatureAlgorithmPreferences();
            var claimMapper = new IdentityMapper();
            var httpConfig = HttpJwksLoaderConfig.builder()
                    .jwksUrl(TEST_JWKS_URL)
                    .build();

            // When
            var config = IssuerConfig.builder()
                    .expectedAudience(audience)
                    .expectedClientId(clientId)
                    .algorithmPreferences(algorithmPreferences)
                    .claimMapper("test-claim", claimMapper)
                    .httpJwksLoaderConfig(httpConfig)
                    .build();

            // Then
            assertEquals(Set.of(audience), config.getExpectedAudience());
            assertEquals(Set.of(clientId), config.getExpectedClientId());
            assertEquals(algorithmPreferences, config.getAlgorithmPreferences());
            assertEquals(claimMapper, config.getClaimMappers().get("test-claim"));
            assertNotNull(config.getJwksLoader());
        }
    }

    @Nested
    @DisplayName("Tests for initJWKSLoader")
    class InitSecurityEventCounterTests {

        @Test
        @DisplayName("Should initialize with HTTP JwksLoader")
        void shouldInitializeWithHttpJwksLoader() {
            // Given
            var config = IssuerConfig.builder()
                    .httpJwksLoaderConfig(HttpJwksLoaderConfig.builder()
                            .jwksUrl(TEST_JWKS_URL)
                            .build())
                    .build();
            var securityEventCounter = new SecurityEventCounter();

            // When
            config.initSecurityEventCounter(securityEventCounter);

            // Then
            assertNotNull(config.getJwksLoader());
        }

        @Test
        @DisplayName("Should initialize with file JwksLoader")
        void shouldInitializeWithFileJwksLoader(@TempDir Path tempDir) throws IOException {
            // Given
            var jwksFilePath = tempDir.resolve("jwks.json");
            Files.writeString(jwksFilePath, TEST_JWKS_CONTENT);

            var config = IssuerConfig.builder()
                    .issuerIdentifier("test-issuer")
                    .jwksFilePath(jwksFilePath.toString())
                    .build();
            var securityEventCounter = new SecurityEventCounter();

            // When
            config.initSecurityEventCounter(securityEventCounter);

            // Then
            assertNotNull(config.getJwksLoader());
        }

        @Test
        @DisplayName("Should initialize with in-memory JwksLoader")
        void shouldInitializeWithInMemoryJwksLoader() {
            // Given
            var config = IssuerConfig.builder()
                    .issuerIdentifier("test-issuer")
                    .jwksContent(TEST_JWKS_CONTENT)
                    .build();
            var securityEventCounter = new SecurityEventCounter();

            // When
            config.initSecurityEventCounter(securityEventCounter);

            // Then
            assertNotNull(config.getJwksLoader());
            JwksLoader jwksLoader = config.getJwksLoader();
            LOGGER.debug("JwksLoader initialized: {}", jwksLoader);
        }

        @Test
        @DisplayName("Should throw exception during build when no JwksLoader config (validation happens during construction)")
        void shouldThrowExceptionDuringBuildWhenNoJwksLoaderConfig() {
            // Given
            var builder = IssuerConfig.builder();

            // When/Then - build should now throw since validation happens during construction
            var exception = assertThrows(IllegalArgumentException.class, builder::build);

            assertTrue(exception.getMessage().contains("No JwksLoader configuration is present"));
        }

        @Test
        @DisplayName("Should throw exception when securityEventCounter is null")
        void shouldThrowExceptionWhenSecurityEventCounterIsNull() {
            // Given
            var config = IssuerConfig.builder()
                    .issuerIdentifier("test-issuer")
                    .jwksContent(TEST_JWKS_CONTENT)
                    .build();

            // When/Then
            assertThrows(NullPointerException.class,
                    () -> config.initSecurityEventCounter(null));
        }
    }

    @Nested
    @DisplayName("Tests for configuration validation during build")
    class ValidationTests {

        @Test
        @DisplayName("Should build successful configuration with JWKS content")
        void shouldBuildSuccessfulConfigurationWithJwksContent() {
            // Given/When/Then - should not throw during build
            assertDoesNotThrow(() -> {
                IssuerConfig.builder()
                        .issuerIdentifier("test-issuer")
                        .jwksContent(TEST_JWKS_CONTENT)
                        .build();
            });
        }

        @Test
        @DisplayName("Should build successful configuration with HTTP JWKS")
        void shouldBuildSuccessfulConfigurationWithHttpJwks() {
            // Given
            var httpConfig = HttpJwksLoaderConfig.builder()
                    .jwksUrl("https://example.com/.well-known/jwks.json")
                    .build();

            // When/Then - should not throw during build (issuerIdentifier not required for well-known)
            assertDoesNotThrow(() -> {
                IssuerConfig.builder()
                        .httpJwksLoaderConfig(httpConfig)
                        .build();
            });
        }

        @Test
        @DisplayName("Should throw exception when no JWKS configuration is present")
        void shouldThrowExceptionWhenNoJwksConfigurationIsPresent() {
            // Given
            var builder = IssuerConfig.builder()
                    .issuerIdentifier("test-issuer");

            // When/Then - should throw during build
            var exception = assertThrows(IllegalArgumentException.class, builder::build);
            assertTrue(exception.getMessage().contains("No JwksLoader configuration is present"));
        }

        @Test
        @DisplayName("Should throw exception when issuerIdentifier missing for in-memory JWKS")
        void shouldThrowExceptionWhenIssuerIdentifierMissingForInMemoryJwks() {
            // Given
            var builder = IssuerConfig.builder()
                    .jwksContent(TEST_JWKS_CONTENT);

            // When/Then - should throw during build
            var exception = assertThrows(IllegalArgumentException.class, builder::build);
            assertTrue(exception.getMessage().contains("issuerIdentifier is required"));
        }

        @Test
        @DisplayName("Should throw exception when issuerIdentifier missing for file-based JWKS")
        void shouldThrowExceptionWhenIssuerIdentifierMissingForFileBasedJwks() {
            // Given
            var builder = IssuerConfig.builder()
                    .jwksFilePath("/path/to/jwks.json");

            // When/Then - should throw during build
            var exception = assertThrows(IllegalArgumentException.class, builder::build);
            assertTrue(exception.getMessage().contains("issuerIdentifier is required"));
        }

        @Test
        @DisplayName("Should skip validation for disabled issuer")
        void shouldSkipValidationForDisabledIssuer() {
            // When/Then - should not throw even with missing configuration
            assertDoesNotThrow(() -> {
                IssuerConfig.builder()
                        .enabled(false)
                        .build();
            });
        }
    }
}
