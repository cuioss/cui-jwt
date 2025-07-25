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
package de.cuioss.jwt.validation.jwks.parser;

import de.cuioss.jwt.validation.JWTValidationLogMessages.WARN;
import de.cuioss.jwt.validation.jwks.key.JwkKeyConstants;
import de.cuioss.jwt.validation.jwks.key.JwkKeyHandler;
import de.cuioss.jwt.validation.jwks.key.KeyInfo;
import de.cuioss.jwt.validation.security.JwkAlgorithmPreferences;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.jwt.validation.security.SecurityEventCounter.EventType;
import de.cuioss.tools.logging.CuiLogger;
import jakarta.json.JsonObject;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.security.spec.InvalidKeySpecException;
import java.util.Optional;

/**
 * Processes individual JWK objects and creates KeyInfo instances.
 * This class is responsible for:
 * <ul>
 *   <li>Processing RSA keys</li>
 *   <li>Processing EC keys</li>
 *   <li>Determining appropriate algorithms</li>
 *   <li>Error handling and logging</li>
 * </ul>
 */
@RequiredArgsConstructor
public class KeyProcessor {

    private static final CuiLogger LOGGER = new CuiLogger(KeyProcessor.class);
    private static final String RSA_KEY_TYPE = "RSA";
    private static final String EC_KEY_TYPE = "EC";

    @NonNull
    private final SecurityEventCounter securityEventCounter;

    @NonNull
    private final JwkAlgorithmPreferences jwkAlgorithmPreferences;

    /**
     * Process a JWK object and create a KeyInfo with validation.
     * 
     * @param jwk the JWK object to process
     * @return an Optional containing the KeyInfo if processing succeeded, empty otherwise
     */
    public Optional<KeyInfo> processKey(JsonObject jwk) {
        // Validate key parameters first
        if (!validateKeyParameters(jwk)) {
            return Optional.empty();
        }

        // Extract key type
        var keyType = JwkKeyConstants.KeyType.getString(jwk);
        if (keyType.isEmpty()) {
            LOGGER.warn(WARN.JWK_MISSING_KTY::format);
            securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
            return Optional.empty();
        }

        String kty = keyType.get();
        String kid = JwkKeyConstants.KeyId.from(jwk).orElse("default-key-id");

        KeyInfo keyInfo = switch (kty) {
            case RSA_KEY_TYPE -> processRsaKey(jwk, kid);
            case EC_KEY_TYPE -> processEcKey(jwk, kid);
            default -> {
                LOGGER.debug("Unsupported key type: %s for key ID: %s", kty, kid);
                yield null;
            }
        };

        return Optional.ofNullable(keyInfo);
    }

    /**
     * Validates individual key parameters and algorithms.
     * 
     * @param keyObject the individual key object to validate
     * @return true if the key is valid, false otherwise
     */
    private boolean validateKeyParameters(JsonObject keyObject) {
        // Validate required key type
        if (!JwkKeyConstants.KeyType.isPresent(keyObject)) {
            LOGGER.warn(WARN.JWK_KEY_MISSING_KTY::format);
            securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
            return false;
        }

        String keyType = keyObject.getString(JwkKeyConstants.KeyType.KEY);

        // Validate key type is supported
        if (!"RSA".equals(keyType) && !"EC".equals(keyType)) {
            LOGGER.warn(WARN.JWK_UNSUPPORTED_KEY_TYPE.format(keyType));
            securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
            return false;
        }

        // Validate key ID if present (length check)
        if (keyObject.containsKey(JwkKeyConstants.KeyId.KEY)) {
            String keyId = keyObject.getString(JwkKeyConstants.KeyId.KEY);
            if (keyId.length() > 100) {
                LOGGER.warn(WARN.JWK_KEY_ID_TOO_LONG.format(keyId.length()));
                securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
                return false;
            }
        }

        // Validate algorithm if present
        if (keyObject.containsKey(JwkKeyConstants.Algorithm.KEY)) {
            String algorithm = keyObject.getString(JwkKeyConstants.Algorithm.KEY);
            if (!jwkAlgorithmPreferences.isSupported(algorithm)) {
                LOGGER.warn(WARN.JWK_INVALID_ALGORITHM.format(algorithm));
                securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
                return false;
            }
        }

        return true;
    }

    /**
     * Process an RSA key and create a KeyInfo object.
     *
     * @param jwk the JWK object
     * @param kid the key ID
     * @return the KeyInfo object or null if processing failed
     */
    private KeyInfo processRsaKey(JsonObject jwk, String kid) {
        try {
            var publicKey = JwkKeyHandler.parseRsaKey(jwk);
            // Determine algorithm if not specified
            String alg = JwkKeyConstants.Algorithm.from(jwk).orElse("RS256"); // Default to RS256
            LOGGER.debug("Parsed RSA key with ID: %s and algorithm: %s", kid, alg);
            return new KeyInfo(publicKey, alg, kid);
        } catch (InvalidKeySpecException | IllegalStateException e) {
            LOGGER.warn(e, WARN.RSA_KEY_PARSE_FAILED.format(kid, e.getMessage()));
            securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
            return null;
        }
    }

    /**
     * Process an EC key and create a KeyInfo object.
     *
     * @param jwk the JWK object
     * @param kid the key ID
     * @return the KeyInfo object or null if processing failed
     */
    private KeyInfo processEcKey(JsonObject jwk, String kid) {
        try {
            var publicKey = JwkKeyHandler.parseEcKey(jwk);
            // Determine algorithm
            String alg = determineEcAlgorithm(jwk);
            LOGGER.debug("Parsed EC key with ID: %s and algorithm: %s", kid, alg);
            return new KeyInfo(publicKey, alg, kid);
        } catch (InvalidKeySpecException | IllegalStateException e) {
            LOGGER.warn(e, WARN.EC_KEY_PARSE_FAILED.format(kid, e.getMessage()));
            securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
            return null;
        }
    }

    /**
     * Determine the EC algorithm from the JWK.
     * 
     * @param jwk the JWK object
     * @return the algorithm
     */
    private String determineEcAlgorithm(JsonObject jwk) {
        var algOption = JwkKeyConstants.Algorithm.from(jwk);
        if (algOption.isPresent()) {
            return algOption.get();
        }

        // Determine algorithm based on curve
        String curve = JwkKeyConstants.Curve.from(jwk).orElse("P-256");
        return JwkKeyHandler.determineEcAlgorithm(curve);
    }
}