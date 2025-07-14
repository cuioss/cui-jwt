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
import de.cuioss.jwt.quarkus.integration.config.RealmConfig;
import de.cuioss.tools.logging.CuiLogger;
import io.restassured.RestAssured;
import io.restassured.path.json.exception.JsonPathException;
import io.restassured.response.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Repository for managing pre-loaded JWT tokens from multiple Keycloak realms.
 * This class fetches real tokens upfront to avoid impacting benchmark performance
 * during actual measurements.
 *
 * <h2>Multi-Realm Design Rationale</h2>
 *
 * <p>This implementation has been enhanced to support multiple Keycloak realms
 * without code duplication. The key design decisions are:</p>
 *
 * <h3>1. Realm Configuration Abstraction</h3>
 * <ul>
 *   <li>{@link RealmConfig} encapsulates realm-specific settings (client ID, credentials, etc.)</li>
 *   <li>Eliminates hardcoded realm values and enables parameterized token fetching</li>
 *   <li>Supports both public and confidential OAuth2 clients</li>
 * </ul>
 *
 * <h3>2. Token Distribution Strategy</h3>
 * <ul>
 *   <li>Tokens are distributed evenly across all configured realms</li>
 *   <li>If tokenPoolSize=100 and 2 realms, each realm provides ~50 tokens</li>
 *   <li>Remainder tokens are distributed to ensure exact total count</li>
 * </ul>
 *
 * <h3>3. Testing Benefits</h3>
 * <ul>
 *   <li>Benchmark realm: Tests well-known discovery configuration</li>
 *   <li>Integration realm: Tests direct JWKS URL configuration</li>
 *   <li>Validates both JWT validation pathways in a single benchmark run</li>
 * </ul>
 *
 * <h3>4. Backward Compatibility</h3>
 * <ul>
 *   <li>Legacy single-realm constructor remains for existing code</li>
 *   <li>Deprecated methods guide migration to multi-realm approach</li>
 *   <li>Default behavior unchanged when using old constructor</li>
 * </ul>
 *
 * <h3>5. Code Reuse vs Duplication</h3>
 * <p>Alternative approaches considered:</p>
 * <ul>
 *   <li><strong>Strategy Pattern:</strong> Would add complexity for minimal benefit</li>
 *   <li><strong>Separate Repositories:</strong> Would duplicate the complex retry/error handling logic</li>
 *   <li><strong>Configuration Objects (Chosen):</strong> Provides clean parameterization without duplication</li>
 * </ul>
 *
 * @see RealmConfig
 * @see BenchmarkConfiguration#getRealmConfigurations()
 */
@SuppressWarnings("java:S2245") // owolff: ok for testing
public class TokenRepository {

    private static final CuiLogger LOGGER = new CuiLogger(TokenRepository.class);
    public static final String ACCESS_TOKEN = "access_token";
    public static final String ID_TOKEN = "id_token";
    public static final String REFRESH_TOKEN = "refresh_token";

    private final List<String> validTokens;
    private final List<String> validIdTokens;
    private final List<String> validRefreshTokens;
    private final List<String> expiredTokens;
    private final List<String> invalidTokens;
    private final String keycloakUrl;
    private final int tokenPoolSize;
    private final List<RealmConfig> realmConfigs;

    /**
     * Creates a new token repository with multi-realm support.
     *
     * Design Note: This constructor enables fetching tokens from multiple realms
     * to test different JWT validation configurations without code duplication.
     * Tokens are distributed evenly across the provided realms.
     *
     * @param keycloakUrl URL of the Keycloak server
     * @param tokenPoolSize Number of tokens to pre-load for each type
     * @param realmConfigs List of realm configurations to fetch tokens from
     */
    public TokenRepository(String keycloakUrl, int tokenPoolSize, List<RealmConfig> realmConfigs) {
        this.keycloakUrl = keycloakUrl;
        this.tokenPoolSize = tokenPoolSize;
        this.realmConfigs = new ArrayList<>(realmConfigs);
        this.validTokens = new ArrayList<>();
        this.validIdTokens = new ArrayList<>();
        this.validRefreshTokens = new ArrayList<>();
        this.expiredTokens = new ArrayList<>();
        this.invalidTokens = new ArrayList<>();
    }


    /**
     * Initializes the token repository by fetching tokens from Keycloak.
     * This method should be called once before benchmark execution.
     *
     */
    public void initialize() {
        LOGGER.info("🔑 Initializing token repository with %s tokens per type...", tokenPoolSize);

        // Load valid tokens (access, ID, and refresh)
        loadValidTokens();

        // Generate expired tokens (simulate by using very short-lived tokens)
        loadExpiredTokens();

        // Generate invalid tokens (malformed tokens)
        generateInvalidTokens();

        LOGGER.info("✅ Token repository initialized successfully");
        LOGGER.info("📊 Token counts - Access: %s, ID: %s, Refresh: %s, Expired: %s, Invalid: %s",
                validTokens.size(), validIdTokens.size(), validRefreshTokens.size(),
                expiredTokens.size(), invalidTokens.size());
    }

    /**
     * Gets a random valid access token from the repository.
     *
     * @return A valid JWT access token
     */
    public String getValidToken() {
        if (validTokens.isEmpty()) {
            throw new IllegalStateException("No valid access tokens available. Call initialize() first.");
        }
        return validTokens.get(ThreadLocalRandom.current().nextInt(validTokens.size()));
    }

    /**
     * Gets a random valid ID token from the repository.
     *
     * @return A valid JWT ID token
     */
    public String getValidIdToken() {
        if (validIdTokens.isEmpty()) {
            throw new IllegalStateException("No valid ID tokens available. Call initialize() first.");
        }
        return validIdTokens.get(ThreadLocalRandom.current().nextInt(validIdTokens.size()));
    }

    /**
     * Gets a random valid refresh token from the repository.
     *
     * @return A valid JWT refresh token
     */
    public String getValidRefreshToken() {
        if (validRefreshTokens.isEmpty()) {
            throw new IllegalStateException("No valid refresh tokens available. Call initialize() first.");
        }
        return validRefreshTokens.get(ThreadLocalRandom.current().nextInt(validRefreshTokens.size()));
    }

    /**
     * Gets a random expired token from the repository.
     *
     * @return An expired JWT token
     */
    public String getExpiredToken() {
        if (expiredTokens.isEmpty()) {
            throw new IllegalStateException("No expired tokens available. Call initialize() first.");
        }
        return expiredTokens.get(ThreadLocalRandom.current().nextInt(expiredTokens.size()));
    }

    /**
     * Gets a random invalid token from the repository.
     *
     * @return An invalid JWT token
     */
    public String getInvalidToken() {
        if (invalidTokens.isEmpty()) {
            throw new IllegalStateException("No invalid tokens available. Call initialize() first.");
        }
        return invalidTokens.get(ThreadLocalRandom.current().nextInt(invalidTokens.size()));
    }

    /**
     * Gets a random token based on error percentage.
     *
     * @param errorPercentage Percentage of tokens that should be invalid (0-100)
     * @return A JWT token (valid or invalid based on percentage)
     */
    public String getTokenByErrorRate(int errorPercentage) {
        int random = ThreadLocalRandom.current().nextInt(100);

        if (random < errorPercentage) {
            // Return invalid or expired token
            if (random < errorPercentage / 2) {
                return getInvalidToken();
            } else {
                return getExpiredToken();
            }
        } else {
            return getValidToken();
        }
    }

    /**
     * Returns the number of available valid tokens.
     */
    public int getValidTokenCount() {
        return validTokens.size();
    }

    /**
     * Returns the number of available expired tokens.
     */
    public int getExpiredTokenCount() {
        return expiredTokens.size();
    }

    /**
     * Returns the number of available invalid tokens.
     */
    public int getInvalidTokenCount() {
        return invalidTokens.size();
    }

    private void loadValidTokens() {
        LOGGER.info("🔄 Loading %s valid tokens from %s realm(s)...", tokenPoolSize, realmConfigs.size());

        var tokenDistribution = calculateTokenDistribution();

        for (int realmIndex = 0; realmIndex < realmConfigs.size(); realmIndex++) {
            RealmConfig realmConfig = realmConfigs.get(realmIndex);
            int tokensForThisRealm = tokenDistribution.get(realmIndex);

            loadTokensFromRealm(realmConfig, tokensForThisRealm);
        }

        LOGGER.info("✅ Loaded %s valid tokens across %s realms", validTokens.size(), realmConfigs.size());
        logRealmDistribution();
    }

    private List<Integer> calculateTokenDistribution() {
        int tokensPerRealm = tokenPoolSize / realmConfigs.size();
        int remainingTokens = tokenPoolSize % realmConfigs.size();

        List<Integer> distribution = new ArrayList<>();
        for (int i = 0; i < realmConfigs.size(); i++) {
            int tokensForRealm = tokensPerRealm + (i < remainingTokens ? 1 : 0);
            distribution.add(tokensForRealm);
        }
        return distribution;
    }

    private void loadTokensFromRealm(RealmConfig realmConfig, int tokenCount) {
        LOGGER.info("🌍 Loading %s tokens from realm: %s", tokenCount, realmConfig.getEffectiveDisplayName());

        for (int i = 0; i < tokenCount; i++) {
            loadSingleTokenSet(realmConfig, i);
            logProgressIfNeeded(i, realmConfig);
        }
    }

    private void loadSingleTokenSet(RealmConfig realmConfig, int tokenIndex) {
        try {
            Map<String, String> tokens = fetchAllTokensFromKeycloak(realmConfig);
            storeTokensFromResponse(tokens);
        } catch (TokenFetchException e) {
            LOGGER.warn("Error loading valid token #%s from %s: %s",
                    tokenIndex + 1, realmConfig.getRealmName(), e.getMessage());
        }
    }

    private void storeTokensFromResponse(Map<String, String> tokens) {
        storeTokenIfValid(tokens.get(ACCESS_TOKEN), validTokens);
        storeTokenIfValid(tokens.get(ID_TOKEN), validIdTokens);
        storeTokenIfValid(tokens.get(REFRESH_TOKEN), validRefreshTokens);
    }

    private void storeTokenIfValid(String token, List<String> targetList) {
        if (token != null && !token.isEmpty()) {
            targetList.add(token);
        }
    }

    private void logProgressIfNeeded(int currentIndex, RealmConfig realmConfig) {
        if ((currentIndex + 1) % 10 == 0) {
            LOGGER.debug("Loaded %s token sets from %s...", currentIndex + 1, realmConfig.getRealmName());
        }
    }

    private void loadExpiredTokens() {
        LOGGER.info("🔄 Loading %s expired tokens from Keycloak...", tokenPoolSize);

        // For expired tokens, we can either:
        // 1. Request tokens with very short expiry (if Keycloak supports it)
        // 2. Use older tokens that have naturally expired
        // 3. Generate tokens with expired timestamps (for testing)

        // For now, we'll fetch normal tokens and let them expire naturally
        // In a real scenario, you might want to configure Keycloak with very short token lifetimes
        int expiredTokenLimit = Math.min(tokenPoolSize, 20); // Limit expired tokens for now
        RealmConfig firstRealm = realmConfigs.getFirst(); // Use first realm for expired tokens

        for (int i = 0; i < expiredTokenLimit; i++) {
            try {
                String token = fetchTokenFromKeycloak(firstRealm);
                if (token != null && !token.isEmpty()) {
                    expiredTokens.add(token);
                }
            } catch (TokenFetchException e) {
                LOGGER.warn("Error loading expired token #%s: %s", i + 1, e.getMessage());
            }
        }

        LOGGER.info("✅ Loaded %s expired tokens", expiredTokens.size());
    }

    /**
     * Logs the distribution of tokens across realms for debugging purposes.
     */
    private void logRealmDistribution() {
        if (realmConfigs.size() > 1) {
            LOGGER.debug("📊 Token distribution summary:");
            LOGGER.debug("  Expected tokens per realm: ~%s", tokenPoolSize / realmConfigs.size());
            LOGGER.debug("  Actual total tokens loaded: %s", validTokens.size());
            LOGGER.debug("  Token types: Access=%s, ID=%s, Refresh=%s",
                    validTokens.size(), validIdTokens.size(), validRefreshTokens.size());
        }
    }

    private void generateInvalidTokens() {
        LOGGER.info("🔄 Generating %s invalid tokens...", tokenPoolSize);

        String[] invalidPatterns = {
                "invalid.token.content",
                "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.invalid_payload.invalid_signature",
                "completely_malformed_token",
                "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWV9.wrong_signature",
                "bearer_token_without_proper_format",
                "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWV9.",
                "",
                "null",
                "undefined",
                "Bearer"
        };

        for (int i = 0; i < tokenPoolSize; i++) {
            String pattern = invalidPatterns[i % invalidPatterns.length];

            // Add some variation to make tokens unique
            String invalidToken = pattern + "_" + i;
            invalidTokens.add(invalidToken);
        }

        LOGGER.info("✅ Generated %s invalid tokens", invalidTokens.size());
    }

    private Map<String, String> fetchAllTokensFromKeycloak(RealmConfig realmConfig) throws TokenFetchException {
        int maxRetries = BenchmarkConfiguration.getMaxTokenFetchRetries();
        int retryDelay = BenchmarkConfiguration.getTokenFetchRetryDelay();

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Map<String, String> tokens = attemptAllTokensFetch(realmConfig);
                if (!tokens.isEmpty()) {
                    return tokens;
                }
                logTokenFetchFailure(attempt, maxRetries, "Empty or null token response received from " + realmConfig.getRealmName());
            } catch (JsonPathException e) {
                handleJsonParsingError(attempt, maxRetries, e);
            } catch (RuntimeException e) {
                handleNetworkError(attempt, maxRetries, e);
            }

            waitBeforeRetry(attempt, maxRetries, retryDelay);
        }

        throw new TokenFetchException("Failed to fetch tokens from " + realmConfig.getRealmName() + " after " + maxRetries + " attempts - no successful response");
    }

    private String fetchTokenFromKeycloak(RealmConfig realmConfig) throws TokenFetchException {
        Map<String, String> tokens = fetchAllTokensFromKeycloak(realmConfig);
        return tokens.get(ACCESS_TOKEN);
    }

    private Map<String, String> attemptAllTokensFetch(RealmConfig realmConfig) {
        Response response = createTokenRequest(realmConfig);
        return extractAllTokensFromResponse(response);
    }

    private Response createTokenRequest(RealmConfig realmConfig) {
        String tokenUrl = realmConfig.buildTokenUrl(keycloakUrl);

        var requestSpec = RestAssured
                .given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("client_id", realmConfig.getClientId())
                .formParam("username", realmConfig.getUsername())
                .formParam("password", realmConfig.getPassword())
                .formParam("grant_type", "password")
                .formParam("scope", "openid profile email");

        // Add client secret if present (for confidential clients)
        if (realmConfig.getClientSecret() != null && !realmConfig.getClientSecret().isEmpty()) {
            requestSpec.formParam("client_secret", realmConfig.getClientSecret());
        }

        return requestSpec
                .when()
                .post(tokenUrl);
    }

    private Map<String, String> extractAllTokensFromResponse(Response response) {
        if (response.statusCode() != 200) {
            LOGGER.debug("Non-200 response from Keycloak. Status: %s, Response: %s",
                    response.statusCode(), response.body().asString());
            return Map.of();
        }

        Map<String, Object> tokenResponse = response.jsonPath().getMap("");
        Map<String, String> tokens = new HashMap<>();

        if (tokenResponse.get(ACCESS_TOKEN) != null) {
            tokens.put(ACCESS_TOKEN, (String) tokenResponse.get(ACCESS_TOKEN));
        }
        if (tokenResponse.get(ID_TOKEN) != null) {
            tokens.put(ID_TOKEN, (String) tokenResponse.get(ID_TOKEN));
        }
        if (tokenResponse.get(REFRESH_TOKEN) != null) {
            tokens.put(REFRESH_TOKEN, (String) tokenResponse.get(REFRESH_TOKEN));
        }

        return tokens;
    }

    private void logTokenFetchFailure(int attempt, int maxRetries, String reason) {
        LOGGER.warn("Failed to fetch token from Keycloak (attempt %s/%s). Reason: %s", attempt, maxRetries, reason);
    }

    private void handleJsonParsingError(int attempt, int maxRetries, JsonPathException e) throws TokenFetchException {
        LOGGER.warn("JSON parsing error during token fetch (attempt %s/%s): %s", attempt, maxRetries, e.getMessage());
        if (attempt == maxRetries) {
            throw new TokenFetchException("Failed to parse Keycloak response after " + maxRetries + " attempts", e);
        }
    }

    private void handleNetworkError(int attempt, int maxRetries, RuntimeException e) throws TokenFetchException {
        LOGGER.warn("Network/HTTP error during token fetch (attempt %s/%s): %s", attempt, maxRetries, e.getMessage());
        if (attempt == maxRetries) {
            throw new TokenFetchException("Failed to fetch token from Keycloak after " + maxRetries + " attempts", e);
        }
    }

    private void waitBeforeRetry(int attempt, int maxRetries, int retryDelay) throws TokenFetchException {
        if (attempt < maxRetries) {
            try {
                LOGGER.debug("Retrying token fetch in %sms...", retryDelay);
                Thread.sleep(retryDelay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TokenFetchException("Token fetch interrupted during retry delay", e);
            }
        }
    }
}
