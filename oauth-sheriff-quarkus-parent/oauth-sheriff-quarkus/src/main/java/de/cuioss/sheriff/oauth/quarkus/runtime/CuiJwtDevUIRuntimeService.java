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
package de.cuioss.sheriff.oauth.quarkus.runtime;

import de.cuioss.sheriff.oauth.library.IssuerConfig;
import de.cuioss.sheriff.oauth.library.TokenValidator;
import de.cuioss.sheriff.oauth.library.domain.token.TokenContent;
import de.cuioss.sheriff.oauth.library.exception.TokenValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime JSON-RPC service for CUI JWT DevUI.
 * <p>
 * This service provides runtime methods for JWT validation and status information
 * for the DevUI components. Unlike the build-time service, this provides actual
 * runtime functionality.
 * </p>
 */
@ApplicationScoped
public class CuiJwtDevUIRuntimeService {

    // String constants for commonly used literals
    private static final String RUNTIME = "RUNTIME";
    private static final String JWT_VALIDATION_DISABLED = "JWT validation is disabled";
    private static final String MESSAGE = "message";
    private static final String VALID = "valid";
    private static final String ERROR = "error";
    private static final String TOKEN_TYPE = "tokenType";
    private static final String CLAIMS = "claims";
    private static final String ISSUER = "issuer";
    private static final String HEALTH_STATUS = "healthStatus";

    private final TokenValidator tokenValidator;
    private final List<IssuerConfig> issuerConfigs;

    /**
     * Constructor for dependency injection.
     *
     * @param tokenValidator the token validator
     * @param issuerConfigs the issuer configurations from TokenValidatorProducer
     */
    @Inject
    public CuiJwtDevUIRuntimeService(TokenValidator tokenValidator, List<IssuerConfig> issuerConfigs) {
        this.tokenValidator = tokenValidator;
        this.issuerConfigs = issuerConfigs;
    }

    /**
     * Get runtime JWT validation status.
     *
     * @return A map containing runtime validation status information
     */
   
    public Map<String, Object> getValidationStatus() {
        Map<String, Object> status = new HashMap<>();

        boolean isEnabled = isJwtEnabled();
        status.put("enabled", isEnabled);
        status.put("validatorPresent", tokenValidator != null);
        status.put("status", RUNTIME);

        if (isEnabled) {
            status.put("statusMessage", "JWT validation is active and ready");
        } else {
            status.put("statusMessage", JWT_VALIDATION_DISABLED);
        }

        return status;
    }

    /**
     * Get runtime JWKS endpoint status.
     *
     * @return A map containing runtime JWKS status information
     */
   
    public Map<String, Object> getJwksStatus() {
        Map<String, Object> jwksInfo = new HashMap<>();

        jwksInfo.put("status", RUNTIME);

        boolean isEnabled = isJwtEnabled();
        int allConfiguredIssuers = countAllConfiguredIssuers();

        if (isEnabled) {
            jwksInfo.put(MESSAGE, "JWKS endpoints are configured and active");
            jwksInfo.put("issuersConfigured", allConfiguredIssuers);
        } else {
            jwksInfo.put(MESSAGE, "JWKS endpoints are disabled");
            jwksInfo.put("issuersConfigured", allConfiguredIssuers);
        }

        return jwksInfo;
    }

    /**
     * Get runtime configuration information.
     *
     * @return A map containing runtime configuration information
     */
   
    public Map<String, Object> getConfiguration() {
        Map<String, Object> configMap = new HashMap<>();

        boolean isEnabled = isJwtEnabled();
        configMap.put("enabled", isEnabled);
        configMap.put("healthEnabled", true); // Health is always enabled in runtime
        configMap.put("buildTime", false);
        configMap.put("metricsEnabled", true); // Metrics are always enabled in runtime

        if (isEnabled) {
            configMap.put(MESSAGE, "JWT validation is properly configured");
            configMap.put("issuersCount", countEnabledIssuers());
        } else {
            configMap.put(MESSAGE, JWT_VALIDATION_DISABLED);
            configMap.put("issuersCount", 0);
        }

        return configMap;
    }

    /**
     * Validate a JWT access token using the runtime validator.
     * Only validates access tokens, not ID tokens or refresh tokens.
     *
     * @param token The JWT access token to validate
     * @return A map containing validation result
     */
   
    public Map<String, Object> validateToken(String token) {
        Map<String, Object> result = new HashMap<>();
        // Set default state - token is invalid until proven valid
        result.put(VALID, false);

        if (token == null || token.trim().isEmpty()) {
            result.put(ERROR, "Token is empty or null");
            return result;
        }

        if (!isJwtEnabled()) {
            result.put(ERROR, JWT_VALIDATION_DISABLED);
            return result;
        }

        if (tokenValidator == null) {
            result.put(ERROR, "Token validator is not available");
            return result;
        }

        try {
            // Only validate as access token
            TokenContent tokenContent = tokenValidator.createAccessToken(token.trim());

            result.put(VALID, true);
            result.put(TOKEN_TYPE, "ACCESS_TOKEN");
            result.put(CLAIMS, tokenContent.getClaims());
            result.put(ISSUER, tokenContent.getIssuer());

        } catch (TokenValidationException e) {
            // Token remains invalid (default state)
            result.put(ERROR, e.getMessage());
            result.put("details", "Access token validation failed");
        } catch (JsonException | IllegalArgumentException e) {
            // Handle JSON parsing errors and other token format issues
            result.put(ERROR, "Invalid token format: " + e.getMessage());
            result.put("details", "Token format is invalid");
        }

        return result;
    }

    /**
     * Get runtime health information.
     *
     * @return A map containing runtime health information
     */
   
    public Map<String, Object> getHealthInfo() {
        Map<String, Object> health = new HashMap<>();

        boolean configValid = isJwtEnabled();
        boolean validatorAvailable = tokenValidator != null;

        health.put("configurationValid", configValid);
        health.put("tokenValidatorAvailable", validatorAvailable);
        health.put("securityCounterAvailable", true); // Metrics are always enabled in runtime
        health.put("overallStatus", RUNTIME);

        if (configValid && validatorAvailable) {
            health.put(MESSAGE, "All JWT components are healthy and operational");
            health.put(HEALTH_STATUS, "UP");
        } else if (configValid) {
            health.put(MESSAGE, "Configuration is valid but validator is not available");
            health.put(HEALTH_STATUS, "DOWN");
        } else {
            health.put(MESSAGE, "JWT validation is disabled or misconfigured");
            health.put(HEALTH_STATUS, "DOWN");
        }

        return health;
    }

    /**
     * Helper method to determine if JWT validation is enabled.
     * JWT is considered enabled if there are any enabled issuers configured.
     *
     * @return true if JWT validation is enabled, false otherwise
     */
    private boolean isJwtEnabled() {
        return countEnabledIssuers() > 0;
    }

    /**
     * Counts the number of enabled issuers using the resolved issuer configurations
     * from TokenValidatorProducer. This leverages existing functionality and avoids
     * duplication of configuration parsing logic.
     *
     * @return number of enabled issuers
     */
    private int countEnabledIssuers() {
        // Count only enabled issuers
        return (int) issuerConfigs.stream()
                .filter(IssuerConfig::isEnabled)
                .count();
    }

    /**
     * Counts the total number of configured issuers (both enabled and disabled) using
     * the resolved issuer configurations from TokenValidatorProducer.
     *
     * @return number of all configured issuers
     */
    private int countAllConfiguredIssuers() {
        // Count all configured issuers (enabled and disabled)
        return issuerConfigs.size();
    }
}
