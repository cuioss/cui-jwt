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
package de.cuioss.jwt.validation.jwks;

import de.cuioss.jwt.validation.jwks.http.HttpJwksLoader;
import de.cuioss.jwt.validation.jwks.http.HttpJwksLoaderConfig;
import de.cuioss.jwt.validation.jwks.key.JWKSKeyLoader;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.jwt.validation.test.InMemoryJWKSFactory;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.tools.net.http.client.LoaderStatus;
import de.cuioss.tools.net.http.retry.RetryStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JwksLoaderFactory} that verify different JWKS loading strategies.
 * <p>
 * Verifies requirements:
 * <ul>
 *   <li>CUI-JWT-4.1: JWKS Loading from different sources (HTTP, file, in-memory)</li>
 *   <li>CUI-JWT-4.4: Graceful handling of JWKS loading failures</li>
 *   <li>CUI-JWT-7.2: Security event tracking for JWKS operations</li>
 * </ul>
 *
 * @author Oliver Wolff
 * @see <a href="https://github.com/cuioss/cui-jwt/tree/main/doc/specification/technical-components.adoc#jwks-integration">JWKS Integration Specification</a>
 */
@EnableTestLogger
@DisplayName("Tests for JwksLoaderFactory")
class JwksLoaderFactoryTest {

    private SecurityEventCounter securityEventCounter;
    private String jwksContent;

    @BeforeEach
    void setUp() {
        securityEventCounter = new SecurityEventCounter();
        jwksContent = InMemoryJWKSFactory.createDefaultJwks();
    }

    @Test
    @DisplayName("Should create HTTP loader")
    void shouldCreateHttpLoader() {

        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .jwksUrl("https://example.com/.well-known/jwks.json")
                .retryStrategy(RetryStrategy.none()) // No retry to avoid delays - HTTP functionality tested in ETagAwareHttpHandler
                .build();
        JwksLoader loader = JwksLoaderFactory.createHttpLoader(config);
        loader.initJWKSLoader(securityEventCounter);
        assertNotNull(loader, "Loader should not be null");
        assertInstanceOf(HttpJwksLoader.class, loader, "Loader should be an instance of HttpJwksLoader");
        assertEquals(JwksType.HTTP, loader.getJwksType(), "Loader should have HTTP type");
        // Trigger loading by attempting to get a key, then check health status
        loader.getKeyInfo("test-key-id"); // This triggers loading attempt
        // With retry capability, failed loads no longer set permanent ERROR status
        assertNotEquals(LoaderStatus.ERROR, loader.isHealthy(), "HTTP loader should not have permanent ERROR status (enables retry)");
    }

    @Test
    @DisplayName("Should create well-known discovery loader")
    void shouldCreateWellKnownLoader() {

        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .wellKnownUrl("https://example.com/.well-known/openid-configuration")
                .retryStrategy(RetryStrategy.none()) // No retry to avoid delays - HTTP functionality tested in ETagAwareHttpHandler
                .build();
        JwksLoader loader = JwksLoaderFactory.createHttpLoader(config);
        loader.initJWKSLoader(securityEventCounter);
        assertNotNull(loader, "Loader should not be null");
        assertInstanceOf(HttpJwksLoader.class, loader, "Loader should be an instance of HttpJwksLoader");
        assertEquals(JwksType.WELL_KNOWN, loader.getJwksType(), "Loader should have WELL_KNOWN type");
        // Trigger loading by attempting to get a key, then check health status
        loader.getKeyInfo("test-key-id"); // This triggers loading attempt
        // With retry capability, failed loads no longer set permanent ERROR status
        assertEquals(LoaderStatus.UNDEFINED, loader.isHealthy(), "Well-known loader should have UNDEFINED status (enables retry)");
    }

    @Test
    @DisplayName("Should create file loader")
    void shouldCreateFileLoader(@TempDir Path tempDir) throws IOException {

        Path jwksFile = tempDir.resolve("jwks.json");
        Files.writeString(jwksFile, jwksContent);
        JwksLoader loader = JwksLoaderFactory.createFileLoader(jwksFile.toString());
        loader.initJWKSLoader(securityEventCounter);
        assertNotNull(loader, "Loader should not be null");
        assertInstanceOf(JWKSKeyLoader.class, loader, "Loader should be an instance of JWKSKeyLoader");
        assertEquals(JwksType.FILE, loader.getJwksType(), "Loader should have FILE type");
        assertEquals(LoaderStatus.OK, loader.isHealthy(), "Loader should have OK status for valid file");
    }

    @Test
    @DisplayName("Should fail fast when creating file loader for non-existent file")
    void shouldFailFastForNonExistentFile() {

        String nonExistentFile = "non-existent-file.json";
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> JwksLoaderFactory.createFileLoader(nonExistentFile),
                "Should throw IllegalArgumentException for non-existent file");

        assertTrue(exception.getMessage().contains("Cannot read JWKS file"),
                "Exception message should indicate file read failure");
        assertTrue(exception.getMessage().contains(nonExistentFile),
                "Exception message should contain the file name");
    }

    @Test
    @DisplayName("Should create in-memory loader")
    void shouldCreateInMemoryLoader() {

        JwksLoader loader = JwksLoaderFactory.createInMemoryLoader(jwksContent);
        loader.initJWKSLoader(securityEventCounter);
        assertNotNull(loader, "Loader should not be null");
        assertInstanceOf(JWKSKeyLoader.class, loader, "Loader should be an instance of JWKSKeyLoader");
        assertEquals(JwksType.MEMORY, loader.getJwksType(), "Loader should have MEMORY type");
        assertEquals(LoaderStatus.OK, loader.isHealthy(), "Loader should have OK status for valid content");
    }

    @Test
    @DisplayName("Should create in-memory loader with fallback for invalid content")
    void shouldCreateInMemoryLoaderWithFallbackForInvalidContent() {

        String invalidContent = "invalid-json";
        JwksLoader loader = JwksLoaderFactory.createInMemoryLoader(invalidContent);
        loader.initJWKSLoader(securityEventCounter);
        assertNotNull(loader, "Loader should not be null");
        assertInstanceOf(JWKSKeyLoader.class, loader, "Loader should be an instance of JWKSKeyLoader");
        assertEquals(JwksType.MEMORY, loader.getJwksType(), "Loader should have MEMORY type");
        assertEquals(LoaderStatus.ERROR, loader.isHealthy(), "Loader should have ERROR status for invalid content");

        // The JWKSKeyLoader constructor now automatically increments the counter when it encounters invalid JSON content

        assertEquals(1, securityEventCounter.getCount(SecurityEventCounter.EventType.JWKS_JSON_PARSE_FAILED),
                "Should count JWKS_JSON_PARSE_FAILED event");
    }

    @Test
    @DisplayName("Should return correct providesIssuerIdentifier for all JwksType values")
    void shouldReturnCorrectProvidesIssuerIdentifierForAllJwksTypes() {
        // Only WELL_KNOWN should provide issuer identifier
        assertTrue(JwksType.WELL_KNOWN.providesIssuerIdentifier(),
                "WELL_KNOWN should provide issuer identifier");

        // All other types should not provide issuer identifier
        assertFalse(JwksType.HTTP.providesIssuerIdentifier(),
                "HTTP should not provide issuer identifier");
        assertFalse(JwksType.FILE.providesIssuerIdentifier(),
                "FILE should not provide issuer identifier");
        assertFalse(JwksType.MEMORY.providesIssuerIdentifier(),
                "MEMORY should not provide issuer identifier");
        assertFalse(JwksType.NONE.providesIssuerIdentifier(),
                "NONE should not provide issuer identifier");
    }
}
