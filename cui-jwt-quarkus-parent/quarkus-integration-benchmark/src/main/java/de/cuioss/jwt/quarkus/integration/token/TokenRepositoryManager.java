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
package de.cuioss.jwt.quarkus.integration.token;

import de.cuioss.jwt.quarkus.integration.config.BenchmarkConfiguration;
import de.cuioss.tools.logging.CuiLogger;
import lombok.Getter;

/**
 * Singleton manager for the token repository.
 * Ensures that tokens are loaded once and shared across all benchmark classes
 * to avoid impacting performance measurements.
 */
@SuppressWarnings("java:S6548") // owolff: Singleton os ok for testing
public class TokenRepositoryManager {

    private static final CuiLogger LOGGER = new CuiLogger(TokenRepositoryManager.class);

    private TokenRepository tokenRepository;
    @Getter
    private boolean initialized = false;

    private TokenRepositoryManager() {
        // Private constructor for singleton
    }

    /**
     * Gets the singleton instance of the token repository manager.
     * Uses the initialization-on-demand holder pattern for thread safety.
     *
     * @return The token repository manager instance
     */
    public static TokenRepositoryManager getInstance() {
        return InstanceHolder.INSTANCE;
    }

    /**
     * Thread-safe singleton holder using initialization-on-demand pattern.
     */
    private static final class InstanceHolder {
        private static final TokenRepositoryManager INSTANCE = new TokenRepositoryManager();
    }

    /**
     * Initializes the token repository if not already initialized.
     * This method is thread-safe and will only initialize once.
     *
     * @throws TokenFetchException if initialization fails
     */
    public synchronized void initialize() throws TokenFetchException {
        if (!initialized) {
            LOGGER.info("🚀 Initializing token repository manager...");
            BenchmarkConfiguration.logConfiguration();

            String keycloakUrl = BenchmarkConfiguration.getKeycloakUrl();
            int tokenPoolSize = BenchmarkConfiguration.getTokenPoolSize();

            // Use multi-realm configuration for better test coverage
            tokenRepository = new TokenRepository(keycloakUrl, tokenPoolSize, BenchmarkConfiguration.getRealmConfigurations());
            tokenRepository.initialize();

            initialized = true;
            LOGGER.info("✅ Token repository manager initialized successfully");
        } else {
            LOGGER.debug("Token repository already initialized, skipping...");
        }
    }

    /**
     * Gets the token repository instance.
     * Throws an exception if not initialized.
     *
     * @return The token repository
     * @throws IllegalStateException if not initialized
     */
    public TokenRepository getTokenRepository() {
        if (!initialized || tokenRepository == null) {
            throw new IllegalStateException("Token repository not initialized. Call initialize() first.");
        }
        return tokenRepository;
    }

    /**
     * Gets a random valid access token.
     *
     * @return A valid JWT access token
     */
    public String getValidToken() {
        return getTokenRepository().getValidToken();
    }

    /**
     * Gets a random valid ID token.
     *
     * @return A valid JWT ID token
     */
    public String getValidIdToken() {
        return getTokenRepository().getValidIdToken();
    }

    /**
     * Gets a random valid refresh token.
     *
     * @return A valid JWT refresh token
     */
    public String getValidRefreshToken() {
        return getTokenRepository().getValidRefreshToken();
    }

    /**
     * Gets a random expired token.
     *
     * @return An expired JWT token
     */
    public String getExpiredToken() {
        return getTokenRepository().getExpiredToken();
    }

    /**
     * Gets a random invalid token.
     *
     * @return An invalid JWT token
     */
    public String getInvalidToken() {
        return getTokenRepository().getInvalidToken();
    }

    /**
     * Gets a token based on error percentage.
     *
     * @param errorPercentage Percentage of tokens that should be invalid (0-100)
     * @return A JWT token (valid or invalid based on percentage)
     */
    public String getTokenByErrorRate(int errorPercentage) {
        return getTokenRepository().getTokenByErrorRate(errorPercentage);
    }

    /**
     * Gets repository statistics.
     *
     * @return A string with token count statistics
     */
    public String getStatistics() {
        if (!initialized) {
            return "Token repository not initialized";
        }

        return "Token Repository Stats - Valid: %s, Expired: %s, Invalid: %s".formatted(
                tokenRepository.getValidTokenCount(),
                tokenRepository.getExpiredTokenCount(),
                tokenRepository.getInvalidTokenCount());
    }

}