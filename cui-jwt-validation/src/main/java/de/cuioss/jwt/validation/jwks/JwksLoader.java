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

import de.cuioss.jwt.validation.jwks.key.KeyInfo;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.tools.net.http.client.LoaderStatus;
import de.cuioss.tools.net.http.client.LoadingStatusProvider;
import lombok.NonNull;

import java.util.Optional;

/**
 * Interface for loading JSON Web Keys (JWK) from a JWKS source.
 * <p>
 * Implementations can load keys from different sources like HTTP endpoints or files.
 * <p>
 * This interface supports cryptographic agility by providing methods to get keys
 * along with their algorithm information.
 * <p>
 * Usage examples:
 * <p>
 * File-based JWKS loader:
 * <pre>
 * // Create a file-based JWKS loader
 * String jwksFilePath = "/path/to/jwks.json";
 * SecurityEventCounter securityEventCounter = new SecurityEventCounter();
 * JwksLoader fileJwksLoader = JwksLoaderFactory.createFileLoader(jwksFilePath, securityEventCounter);
 *
 * // Get a key by ID
 * String keyId = "my-key-id";
 * Optional&lt;KeyInfo&gt; keyInfo = fileJwksLoader.getKeyInfo(keyId);
 *
 * // Use the key if present
 * keyInfo.ifPresent(info -> {
 *     PublicKey publicKey = info.getKey();
 *     String algorithm = info.getAlgorithm();
 *     // Use the key for signature verification
 * });
 * </pre>
 * <p>
 * HTTP-based JWKS loader:
 * <pre>
 * // Create an HTTP-based JWKS loader with 60-second refresh interval
 * String jwksEndpoint = "https://example.com/.well-known/jwks.json";
 * HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
 *     .jwksUrl(jwksEndpoint)
 *     .refreshIntervalSeconds(60)
 *     .build();
 * SecurityEventCounter securityEventCounter = new SecurityEventCounter();
 * JwksLoader httpJwksLoader = JwksLoaderFactory.createHttpLoader(config, securityEventCounter);
 *
 * // Get a key by ID
 * Optional&lt;KeyInfo&gt; keyInfo = httpJwksLoader.getKeyInfo("my-key-id");
 * </pre>
 * <p>
 * In-memory JWKS loader:
 * <pre>
 * // Create an in-memory JWKS loader
 * String jwksContent = "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"my-key-id\",\"use\":\"sig\",\"alg\":\"RS256\",\"n\":\"...\",\"e\":\"...\"}]}";
 * SecurityEventCounter securityEventCounter = new SecurityEventCounter();
 * JwksLoader inMemoryJwksLoader = JwksLoaderFactory.createInMemoryLoader(jwksContent, securityEventCounter);
 *
 * // Get a key by ID
 * Optional&lt;KeyInfo&gt; keyInfo = inMemoryJwksLoader.getKeyInfo("my-key-id");
 * </pre>
 * <p>
 * For more details on the security aspects, see the
 * <a href="https://github.com/cuioss/cui-jwt/tree/main/doc/specification/security.adoc">Security Specification</a>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@SuppressWarnings("JavadocLinkAsPlainText")
public interface JwksLoader extends LoadingStatusProvider {

    /**
     * Gets a key by its ID.
     *
     * @param kid the key ID
     * @return an Optional containing the key info if found, empty otherwise
     */
    Optional<KeyInfo> getKeyInfo(String kid);

    /**
     * Gets the type of JWKS source used by this loader.
     *
     * @return the JWKS source type
     */
    JwksType getJwksType();

    /**
     * Gets the issuer identifier associated with this JWKS loader.
     * <p>
     * For HTTP-based loaders using well-known discovery, this returns the issuer
     * identifier from the discovery document. For other loaders, this may return
     * an empty Optional if no issuer identifier is configured or available.
     * </p>
     *
     * @return an Optional containing the issuer identifier if available, empty otherwise
     */
    Optional<String> getIssuerIdentifier();

    /**
     * Gets the current loader status without triggering any loading operations.
     * <p>
     * This method must be non-blocking and fail-fast for use in health checks.
     * It returns the current cached status from memory without performing any I/O
     * operations or network requests.
     * </p>
     * <p>
     * This method is required for MicroProfile Health compliance, ensuring that
     * readiness checks can quickly determine the current state without blocking
     * on potentially slow initialization or loading operations.
     * </p>
     *
     * @return the current status from cache/memory, never triggers I/O operations
     * @since 1.1
     */
    LoaderStatus getCurrentStatus();

    /**
     * Initializes the JwksLoader with the provided SecurityEventCounter.
     * <p>
     * This method should be called after construction to complete the initialization
     * of the JWKS loader with the security event counter for tracking security events.
     * </p>
     * <p>
     * This method is not thread-safe and should be called before the object is shared
     * between threads.
     * </p>
     *
     * @param securityEventCounter the counter for security events, must not be null
     * @throws NullPointerException if securityEventCounter is null
     */
    void initJWKSLoader(@NonNull SecurityEventCounter securityEventCounter);
}
