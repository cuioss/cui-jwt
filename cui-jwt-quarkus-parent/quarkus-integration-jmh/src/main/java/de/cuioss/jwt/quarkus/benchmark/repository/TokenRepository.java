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
package de.cuioss.jwt.quarkus.benchmark.repository;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.cuioss.jwt.quarkus.benchmark.config.TokenRepositoryConfig;
import de.cuioss.jwt.quarkus.benchmark.http.HttpClientFactory;
import de.cuioss.tools.logging.CuiLogger;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;


import lombok.NonNull;

/**
 * Repository for fetching and managing JWT tokens from Keycloak for benchmark testing.
 * Implements token caching and rotation to simulate real-world usage patterns.
 * 
 * @since 1.0
 */
public class TokenRepository {

    private static final CuiLogger LOGGER = new CuiLogger(TokenRepository.class);
    private static final Gson GSON = new Gson();

    // Constants for magic numbers
    private static final int DEFAULT_CONNECTION_TIMEOUT = 200;
    private static final int DEFAULT_MAX_CONNECTIONS = 200;
    private static final int DEFAULT_MAX_CONNECTIONS_PER_ROUTE = 100;
    private static final int TOKEN_EXPIRY_SECONDS = 900; // 15 minutes - matches Keycloak realm configuration
    private static final int AGGRESSIVE_REFRESH_THRESHOLD = 300; // Refresh tokens with less than 5 minutes left
    private static final int HTTP_OK = 200;

    private final TokenRepositoryConfig config;
    private final List<TokenInfo> tokenPool;
    private final AtomicInteger tokenIndex;
    private volatile Instant lastRefresh;
    private final HttpClient httpClient;

    /**
     * Creates a new TokenRepository with the given configuration.
     * 
     * @param config the configuration for connecting to Keycloak
     */
    public TokenRepository(@NonNull TokenRepositoryConfig config) {
        this.config = config;
        this.tokenPool = new ArrayList<>(config.getTokenPoolSize());
        this.tokenIndex = new AtomicInteger(0);
        this.lastRefresh = Instant.EPOCH;

        // Get HttpClient from factory based on SSL verification setting
        this.httpClient = config.isVerifySsl() ? 
                HttpClientFactory.getSecureClient() : 
                HttpClientFactory.getInsecureClient();
        LOGGER.debug("Using {} HttpClient from factory", 
                config.isVerifySsl() ? "secure" : "insecure");

        // Initialize token pool
        refreshTokenPool();
    }

    /**
     * Gets the next token from the pool using round-robin rotation.
     * This ensures even distribution and simulates cache miss scenarios.
     * 
     * @return a JWT access token
     */
    @NonNull
    public String getNextToken() {
        if (shouldRefreshTokens()) {
            refreshTokenPool();
        }

        if (tokenPool.isEmpty()) {
            LOGGER.warn("Token pool is empty, fetching single token");
            return fetchSingleToken();
        }

        int index = tokenIndex.getAndIncrement() % tokenPool.size();
        TokenInfo tokenInfo = tokenPool.get(index);

        // Check if this specific token needs refresh - use more aggressive threshold for safety
        int refreshThreshold = Math.max(config.getTokenRefreshThresholdSeconds(), AGGRESSIVE_REFRESH_THRESHOLD);
        if (tokenInfo.isExpiringSoon(refreshThreshold)) {
            LOGGER.debug("Token at index {} is expiring soon (within {} seconds), refreshing", index, refreshThreshold);
            tokenInfo = refreshToken(tokenInfo);
            tokenPool.set(index, tokenInfo);
        }

        return tokenInfo.getAccessToken();
    }

    /**
     * Gets a random token from the pool for testing different cache scenarios.
     * 
     * @return a JWT access token
     */
    @NonNull
    public String getRandomToken() {
        if (shouldRefreshTokens()) {
            refreshTokenPool();
        }

        if (tokenPool.isEmpty()) {
            return fetchSingleToken();
        }

        int randomIndex = ThreadLocalRandom.current().nextInt(tokenPool.size());
        return tokenPool.get(randomIndex).getAccessToken();
    }

    /**
     * Gets an invalid token for error testing scenarios.
     * 
     * @return an invalid JWT token
     */
    @NonNull
    public String getInvalidToken() {
        return "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkludmFsaWQgVG9rZW4iLCJpYXQiOjE1MTYyMzkwMjJ9.invalid_signature";
    }

    /**
     * Gets an expired token for error testing scenarios.
     * 
     * @return an expired JWT token
     */
    @NonNull
    public String getExpiredToken() {
        return "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJrZXkifQ.eyJleHAiOjE1MTYyMzkwMjIsImlhdCI6MTUxNjIzOTAyMiwianRpIjoiZXhwaXJlZC10b2tlbiIsImlzcyI6Imh0dHA6Ly9sb2NhbGhvc3Q6ODA4MC9yZWFsbXMvY3Vpand0LXJlYWxtIiwiYXVkIjoiY3Vpand0LWNsaWVudCIsInN1YiI6InRlc3R1c2VyIiwidHlwIjoiQmVhcmVyIiwiYXpwIjoiY3Vpand0LWNsaWVudCIsInNlc3Npb25fc3RhdGUiOiJzZXNzaW9uLXN0YXRlIiwiYWNyIjoiMSIsInJlYWxtX2FjY2VzcyI6eyJyb2xlcyI6WyJ1c2VyIl19LCJyZXNvdXJjZV9hY2Nlc3MiOnt9LCJzY29wZSI6Im9wZW5pZCBwcm9maWxlIGVtYWlsIiwic2lkIjoic2Vzc2lvbi1pZCIsImVtYWlsX3ZlcmlmaWVkIjp0cnVlLCJwcmVmZXJyZWRfdXNlcm5hbWUiOiJ0ZXN0dXNlciJ9.expired_signature";
    }

    /**
     * Returns the current size of the token pool.
     * 
     * @return the number of tokens in the pool
     */
    public int getTokenPoolSize() {
        return tokenPool.size();
    }

    private boolean shouldRefreshTokens() {
        return tokenPool.isEmpty() ||
                lastRefresh.isBefore(Instant.now().minusSeconds(config.getTokenRefreshThresholdSeconds()));
    }

    private void refreshTokenPool() {
        LOGGER.info("Refreshing token pool with {} tokens", config.getTokenPoolSize());
        tokenPool.clear();

        for (int i = 0; i < config.getTokenPoolSize(); i++) {
            try {
                String token = fetchSingleToken();
                tokenPool.add(new TokenInfo(token, Instant.now()));
            } catch (Exception e) {
                LOGGER.error("Failed to fetch token {} of {}", i + 1, config.getTokenPoolSize(), e);
                // Continue with other tokens
            }
        }

        lastRefresh = Instant.now();
        LOGGER.info("Token pool refreshed with {} tokens", tokenPool.size());
    }

    private String fetchSingleToken() {
        String tokenEndpoint = "%s/realms/%s/protocol/openid-connect/token".formatted(
                config.getKeycloakBaseUrl(), config.getRealm());

        try {
            // Build form data as URL encoded string
            String formData = "grant_type=" + URLEncoder.encode("password", StandardCharsets.UTF_8) +
                    "&client_id=" + URLEncoder.encode(config.getClientId(), StandardCharsets.UTF_8) +
                    "&client_secret=" + URLEncoder.encode(config.getClientSecret(), StandardCharsets.UTF_8) +
                    "&username=" + URLEncoder.encode(config.getUsername(), StandardCharsets.UTF_8) +
                    "&password=" + URLEncoder.encode(config.getPassword(), StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tokenEndpoint))
                    .timeout(Duration.ofMillis(config.getRequestTimeoutMs()))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formData))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == HTTP_OK) {
                return extractAccessToken(response);
            } else {
                handleTokenFetchError(response);
            }
        } catch (RuntimeException e) {
            throw e; // Re-throw runtime exceptions as-is
        } catch (IOException | InterruptedException e) {
            LOGGER.error("Error fetching token from Keycloak", e);
            throw new TokenFetchException("Error fetching token from Keycloak", e);
        }
        throw new TokenFetchException("Unexpected error fetching token");
    }

    private TokenInfo refreshToken(TokenInfo oldToken) {
        try {
            String newToken = fetchSingleToken();
            return new TokenInfo(newToken, Instant.now());
        } catch (Exception e) {
            LOGGER.warn("Failed to refresh token, keeping old token", e);
            // Return the old token to avoid breaking the benchmark
            return oldToken;
        }
    }

    /**
     * Internal class to hold token information including fetch timestamp.
     */
    private static class TokenInfo {
        private final String accessToken;
        private final Instant fetchedAt;

        public TokenInfo(String accessToken, Instant fetchedAt) {
            this.accessToken = accessToken;
            this.fetchedAt = fetchedAt;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public boolean isExpiringSoon(int thresholdSeconds) {
            // Simple heuristic: assume token expires in 1 hour from fetch time
            // Token needs refresh if: current_time > fetch_time + (expiry_time - threshold)
            return fetchedAt.plusSeconds(TOKEN_EXPIRY_SECONDS - thresholdSeconds).isBefore(Instant.now());
        }
    }

    @NonNull
    private String extractAccessToken(@NonNull HttpResponse<String> response) {
        String responseBody = response.body();
        if (responseBody == null || responseBody.isEmpty()) {
            throw new TokenFetchException("Empty response body from token endpoint");
        }

        JsonObject jsonResponse = GSON.fromJson(responseBody, JsonObject.class);
        if (jsonResponse == null || !jsonResponse.has("access_token")) {
            throw new TokenFetchException("No access_token field in response");
        }

        String token = jsonResponse.get("access_token").getAsString();
        if (token == null || token.isEmpty()) {
            throw new TokenFetchException("Access token is null or empty");
        }

        return token;
    }

    private void handleTokenFetchError(@NonNull HttpResponse<String> response) {
        String errorBody = "<no body>";
        try {
            errorBody = response.body();
        } catch (Exception e) {
            LOGGER.debug("Failed to extract error body", e);
        }

        LOGGER.error("Failed to fetch token. Status: {}, Body: {}",
                response.statusCode(), errorBody);

        throw new TokenFetchException(
                "Failed to fetch token from Keycloak. Status: %d, Body: %s".formatted(
                        response.statusCode(), errorBody)
        );
    }

    /**
     * Custom exception for token fetch errors.
     */
    public static class TokenFetchException extends RuntimeException {
        public TokenFetchException(String message) {
            super(message);
        }

        public TokenFetchException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}