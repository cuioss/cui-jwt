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
import lombok.NonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Repository for fetching and managing JWT tokens from Keycloak for benchmark testing.
 * Implements token caching and rotation to simulate real-world usage patterns.
 *
 * @since 1.0
 */
public class TokenRepository {

    private static final CuiLogger LOGGER = new CuiLogger(TokenRepository.class);
    private static final Gson GSON = new Gson();

    private static final int HTTP_OK = 200;

    private final TokenRepositoryConfig config;
    private final List<TokenInfo> tokenPool;
    private final AtomicInteger tokenIndex;
    private final HttpClient httpClient;
    private String expiredToken;

    /**
     * Creates a new TokenRepository with the given configuration.
     *
     * @param config the configuration for connecting to Keycloak
     */
    public TokenRepository(@NonNull TokenRepositoryConfig config) {
        this.config = config;
        this.tokenPool = new ArrayList<>(config.getTokenPoolSize());
        this.tokenIndex = new AtomicInteger(0);

        // Get HttpClient from factory based on SSL verification setting
        this.httpClient = config.isVerifySsl() ?
            HttpClientFactory.getSecureClient() :
            HttpClientFactory.getInsecureClient();
        LOGGER.debug("Using {} HttpClient from factory",
            config.isVerifySsl() ? "secure" : "insecure");

        // Load expired token from resources
        loadExpiredToken();
        
        // Initialize token pool
        initializeTokenPool();
    }

    /**
     * Gets the next token from the pool using round-robin rotation.
     * This ensures even distribution and simulates cache miss scenarios.
     *
     * @return a JWT access token
     */
    @NonNull
    public String getNextToken() {
        if (tokenPool.isEmpty()) {
            LOGGER.warn("Token pool is empty, fetching single token");
            return fetchSingleToken();
        }

        int index = tokenIndex.getAndIncrement() % tokenPool.size();
        return tokenPool.get(index).accessToken();
    }

    /**
     * Gets a random token from the pool for testing different cache scenarios.
     *
     * @return a JWT access token
     */
    @NonNull
    public String getRandomToken() {
        if (tokenPool.isEmpty()) {
            return fetchSingleToken();
        }

        int randomIndex = ThreadLocalRandom.current().nextInt(tokenPool.size());
        return tokenPool.get(randomIndex).accessToken();
    }

    /**
     * Gets an invalid token for error testing scenarios.
     * Returns a real expired token that contains all required claims but has an expired timestamp.
     *
     * @return an expired JWT token with proper structure
     */
    @NonNull
    public String getInvalidToken() {
        if (expiredToken != null) {
            return expiredToken;
        }
        // Fallback to hardcoded expired token if resource loading failed
        LOGGER.warn("Using fallback expired token as resource loading failed");
        return "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICItQlY1bWNOYlFBVXFMVW9wY1VUR0NzYnAtLWRGZTlINVpaSVo4dmxNT0tzIn0.eyJleHAiOjE3MDAwMDAwMDAsImlhdCI6MTcwMDAwMDAwMCwianRpIjoiZTQ3ZDMyMzQtNGU5NC00ZWYzLWE5NWEtOGU5ZjU2YmY5MjQwIiwiaXNzIjoiaHR0cHM6Ly9rZXljbG9hazo4NDQzL3JlYWxtcy9iZW5jaG1hcmsiLCJhdWQiOiJhY2NvdW50Iiwic3ViIjoiYmVuY2htYXJrLXVzZXIiLCJ0eXAiOiJCZWFyZXIiLCJhenAiOiJiZW5jaG1hcmstY2xpZW50Iiwic2Vzc2lvbl9zdGF0ZSI6ImY4MjU0ZTRlLTQxOTQtNGU5My1hOTVhLThlOWY1NmJmOTI0MCIsImFjciI6IjEiLCJyZWFsbV9hY2Nlc3MiOnsicm9sZXMiOlsiZGVmYXVsdC1yb2xlcy1iZW5jaG1hcmsiLCJvZmZsaW5lX2FjY2VzcyIsInVtYV9hdXRob3JpemF0aW9uIl19LCJyZXNvdXJjZV9hY2Nlc3MiOnsiYWNjb3VudCI6eyJyb2xlcyI6WyJtYW5hZ2UtYWNjb3VudCIsIm1hbmFnZS1hY2NvdW50LWxpbmtzIiwidmlldy1wcm9maWxlIl19fSwic2NvcGUiOiJwcm9maWxlIGVtYWlsIiwic2lkIjoiZjgyNTRlNGUtNDE5NC00ZTkzLWE5NWEtOGU5ZjU2YmY5MjQwIiwiZW1haWxfdmVyaWZpZWQiOmZhbHNlLCJwcmVmZXJyZWRfdXNlcm5hbWUiOiJiZW5jaG1hcmstdXNlciJ9.invalid_signature_but_proper_structure_for_testing";
    }

    /**
     * Returns the current size of the token pool.
     *
     * @return the number of tokens in the pool
     */
    public int getTokenPoolSize() {
        return tokenPool.size();
    }

    private void initializeTokenPool() {
        LOGGER.info("Initializing token pool with {} tokens", config.getTokenPoolSize());

        for (int i = 0; i < config.getTokenPoolSize(); i++) {
            String token = fetchSingleToken();
            tokenPool.add(new TokenInfo(token));
        }

        LOGGER.info("Token pool initialized with {} tokens", tokenPool.size());
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
                throw new TokenFetchException("Unexpected error - handleTokenFetchError should have thrown");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TokenFetchException("Token fetch was interrupted", e);
        } catch (IOException e) {
            throw new TokenFetchException("Error fetching token from Keycloak", e);
        }
    }


    /**
     * Internal class to hold token information.
     */
    private record TokenInfo(String accessToken) {

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
        String errorBody = response.body() != null ? response.body() : "<no body>";

        LOGGER.error("Failed to fetch token. Status: {}, Body: {}",
            response.statusCode(), errorBody);

        throw new TokenFetchException(
            "Failed to fetch token from Keycloak. Status: %d, Body: %s".formatted(
                response.statusCode(), errorBody)
        );
    }

    /**
     * Loads the expired token from resources file.
     * This token is used for testing error scenarios in benchmarks.
     */
    private void loadExpiredToken() {
        String resourcePath = "/expired-benchmark-token.txt";
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                LOGGER.warn("Expired token resource not found: {}", resourcePath);
                return;
            }
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder tokenBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    // Skip comments and empty lines
                    if (!line.trim().startsWith("#") && !line.trim().isEmpty()) {
                        tokenBuilder.append(line.trim());
                    }
                }
                
                String token = tokenBuilder.toString();
                if (!token.isEmpty()) {
                    this.expiredToken = token;
                    LOGGER.info("Loaded expired token from resources (length: {})", token.length());
                } else {
                    LOGGER.warn("Expired token file was empty or contained only comments");
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load expired token from resources", e);
        }
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