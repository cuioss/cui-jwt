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
package de.cuioss.jwt.validation.test;

import de.cuioss.jwt.validation.jwks.JwksLoader;
import de.cuioss.jwt.validation.jwks.JwksLoaderFactory;
import de.cuioss.jwt.validation.jwks.JwksType;
import de.cuioss.jwt.validation.jwks.LoaderStatus;
import de.cuioss.jwt.validation.jwks.key.KeyInfo;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import lombok.NonNull;

import java.util.Optional;

/**
 * Test-specific JwksLoader that wraps an in-memory JwksLoader and provides a custom issuer identifier.
 * This is used for testing scenarios where the issuer needs to match test token issuers.
 */
public class TestJwksLoaderWithIssuer implements JwksLoader {

    private final JwksLoader delegate;
    private final String issuer;

    /**
     * Creates a new TestJwksLoaderWithIssuer.
     *
     * @param jwksContent the JWKS content to load
     * @param issuer      the issuer identifier to return
     */
    public TestJwksLoaderWithIssuer(String jwksContent, String issuer) {
        // Create an in-memory JwksLoader using the factory method
        this.delegate = JwksLoaderFactory.createInMemoryLoader(jwksContent);
        this.issuer = issuer;
        // Initialize immediately for testing
        this.delegate.initJWKSLoader(new SecurityEventCounter());
    }

    @Override
    public Optional<KeyInfo> getKeyInfo(String kid) {
        return delegate.getKeyInfo(kid);
    }

    // Removed overrides for methods that no longer exist in JwksLoader interface

    @Override
    public JwksType getJwksType() {
        return JwksType.MEMORY;
    }

    @Override
    public LoaderStatus isHealthy() {
        return delegate.isHealthy();
    }

    @Override
    public Optional<String> getIssuerIdentifier() {
        // Return the configured issuer if the loader is healthy
        if (isHealthy() == LoaderStatus.OK) {
            return Optional.of(issuer);
        }
        return Optional.empty();
    }

    @Override
    public void initJWKSLoader(@NonNull SecurityEventCounter securityEventCounter) {
        // Delegate to the wrapped JwksLoader
        delegate.initJWKSLoader(securityEventCounter);
    }
}