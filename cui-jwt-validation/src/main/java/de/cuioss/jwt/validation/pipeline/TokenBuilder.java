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
package de.cuioss.jwt.validation.pipeline;

import de.cuioss.jwt.validation.IssuerConfig;
import de.cuioss.jwt.validation.domain.claim.ClaimName;
import de.cuioss.jwt.validation.domain.claim.ClaimValue;
import de.cuioss.jwt.validation.domain.claim.mapper.ClaimMapper;
import de.cuioss.jwt.validation.domain.claim.mapper.IdentityMapper;
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.domain.token.IdTokenContent;
import jakarta.json.JsonObject;
import lombok.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Builder for creating token content objects from decoded JWT tokens.
 * <p>
 * This class is responsible for transforming decoded JWT tokens into strongly-typed
 * token content objects for further processing in the application.
 * <p>
 * It supports creating different types of tokens:
 * <ul>
 *   <li>Access Tokens - via {@link #createAccessToken}</li>
 *   <li>ID Tokens - via {@link #createIdToken}</li>
 * </ul>
 * <p>
 * During token creation, the builder extracts and maps claims from the token body
 * using appropriate claim mappers based on the issuer configuration or standard claim names.
 * <p>
 * For more details on the token building process, see the
 * <a href="https://github.com/cuioss/cui-jwt/tree/main/doc/specification/technical-components.adoc#token-validation-pipeline">Token Validation Pipeline</a>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public class TokenBuilder {

    // Typical JWT claim count for pre-sizing
    private static final int TYPICAL_CLAIM_COUNT = 15;

    // Singleton mapper for unknown claims
    private final IdentityMapper identityMapper = new IdentityMapper();

    // Combined mapper lookup to avoid runtime checks
    private final Map<String, ClaimMapper> allMappers;

    /**
     * Constructs a TokenBuilder with the specified IssuerConfig.
     *
     * @param issuerConfig the issuer configuration
     */
    public TokenBuilder(@NonNull IssuerConfig issuerConfig) {
        Map<String, ClaimMapper> customMappers = issuerConfig.getClaimMappers() != null
                ? Map.copyOf(issuerConfig.getClaimMappers())
                : Map.of();

        // Build standard mappers from ClaimName enum
        Map<String, ClaimMapper> tempMappers = new HashMap<>();

        // Iterate over all ClaimName values and get their mappers
        for (ClaimName claimName : ClaimName.values()) {
            tempMappers.put(claimName.getName(), claimName.getClaimMapper());
        }

        // Add custom mappers, which override standard ones
        tempMappers.putAll(customMappers);

        // Create immutable map for thread-safety and performance
        this.allMappers = Map.copyOf(tempMappers);
    }

    /**
     * Creates an AccessTokenContent from a decoded JWT.
     *
     * @param decodedJwt the decoded JWT
     * @return an Optional containing the AccessTokenContent if it could be created, empty otherwise
     */
    public Optional<AccessTokenContent> createAccessToken(@NonNull DecodedJwt decodedJwt) {
        Optional<JsonObject> bodyOption = decodedJwt.getBody();
        if (bodyOption.isEmpty()) {
            return Optional.empty();
        }

        JsonObject body = bodyOption.get();
        Map<String, ClaimValue> claims = extractClaims(body);

        return Optional.of(new AccessTokenContent(claims, decodedJwt.rawToken(), null));
    }

    /**
     * Creates an IdTokenContent from a decoded JWT.
     *
     * @param decodedJwt the decoded JWT
     * @return an Optional containing the IdTokenContent if it could be created, empty otherwise
     */
    public Optional<IdTokenContent> createIdToken(@NonNull DecodedJwt decodedJwt) {
        Optional<JsonObject> bodyOption = decodedJwt.getBody();
        if (bodyOption.isEmpty()) {
            return Optional.empty();
        }

        JsonObject body = bodyOption.get();
        Map<String, ClaimValue> claims = extractClaims(body);

        return Optional.of(new IdTokenContent(claims, decodedJwt.rawToken()));
    }


    /**
     * Extracts claims from a JSON object.
     *
     * @param jsonObject the JSON object containing claims
     * @return a map of claim names to claim values
     */
    private Map<String, ClaimValue> extractClaims(JsonObject jsonObject) {
        // Pre-size HashMap based on typical JWT size to avoid resizing
        int claimCount = jsonObject.size();
        Map<String, ClaimValue> claims = new HashMap<>(Math.max(claimCount, TYPICAL_CLAIM_COUNT));

        // Process all keys in the JSON object
        for (String key : jsonObject.keySet()) {
            // Single lookup for mapper (combines custom and standard)
            ClaimMapper mapper = allMappers.getOrDefault(key, identityMapper);
            claims.put(key, mapper.map(jsonObject, key));
        }

        return claims;
    }

    /**
     * Extracts claims for a Refresh-Token from a JSON object.
     *
     * @param jsonObject the JSON object containing claims
     * @return a map of claim names to claim values
     */
    public static Map<String, ClaimValue> extractClaimsForRefreshToken(@NonNull JsonObject jsonObject) {
        int claimCount = jsonObject.size();
        Map<String, ClaimValue> claims = new HashMap<>(Math.max(claimCount, TYPICAL_CLAIM_COUNT));

        // Create a single IdentityMapper instance for this static method
        IdentityMapper mapper = new IdentityMapper();

        for (String key : jsonObject.keySet()) {
            // Use singleton IdentityMapper
            claims.put(key, mapper.map(jsonObject, key));
        }

        return claims;
    }
}
