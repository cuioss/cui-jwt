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
import de.cuioss.jwt.validation.json.JwkKey;
import de.cuioss.jwt.validation.jwks.key.JwkKeyHandler;
import de.cuioss.jwt.validation.jwks.key.KeyInfo;
import de.cuioss.jwt.validation.security.JwkAlgorithmPreferences;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.jwt.validation.security.SecurityEventCounter.EventType;
import de.cuioss.tools.logging.CuiLogger;
import lombok.NonNull;

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
public class KeyProcessor {

    private static final CuiLogger LOGGER = new CuiLogger(KeyProcessor.class);
    private static final String RSA_KEY_TYPE = "RSA";
    private static final String EC_KEY_TYPE = "EC";

    @NonNull
    private final SecurityEventCounter securityEventCounter;

    @NonNull
    private final JwkAlgorithmPreferences jwkAlgorithmPreferences;

    public KeyProcessor(@NonNull SecurityEventCounter securityEventCounter,
            @NonNull JwkAlgorithmPreferences jwkAlgorithmPreferences) {
        this.securityEventCounter = securityEventCounter;
        this.jwkAlgorithmPreferences = jwkAlgorithmPreferences;
    }

    /**
     * Process a JWK object and create a KeyInfo with validation.
     * 
     * @param jwk the JWK object to process
     * @return an Optional containing the KeyInfo if processing succeeded, empty otherwise
     */
    public Optional<KeyInfo> processKey(JwkKey jwk) {
        // Validate key parameters first
        if (!validateKeyParameters(jwk)) {
            return Optional.empty();
        }

        // Extract key type directly from JwkKey
        String kty = jwk.kty();
        if (kty == null || kty.trim().isEmpty()) {
            LOGGER.warn(WARN.JWK_MISSING_KTY::format);
            securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
            return Optional.empty();
        }

        String kid = jwk.getKid().isPresent() ? jwk.kid() : "default-key-id";

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
    private boolean validateKeyParameters(JwkKey jwkKey) {
        // Validate required key type
        if (jwkKey.kty() == null || jwkKey.kty().trim().isEmpty()) {
            LOGGER.warn(WARN.JWK_MISSING_KTY::format);
            securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
            return false;
        }

        String keyType = jwkKey.kty();

        // Validate key type is supported
        if (!"RSA".equals(keyType) && !"EC".equals(keyType)) {
            LOGGER.warn(WARN.JWK_UNSUPPORTED_KEY_TYPE.format(keyType));
            securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
            return false;
        }

        // Validate key ID if present (length check)
        if (jwkKey.getKid().isPresent()) {
            String keyId = jwkKey.kid();
            if (keyId.length() > 100) {
                LOGGER.warn(WARN.JWK_KEY_ID_TOO_LONG.format(keyId.length()));
                securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
                return false;
            }
        }

        // Validate algorithm if present  
        if (jwkKey.alg() != null) {
            String algorithm = jwkKey.alg();
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
    private KeyInfo processRsaKey(JwkKey jwk, String kid) {
        try {
            var publicKey = JwkKeyHandler.parseRsaKey(jwk);
            // Determine algorithm if not specified
            String alg = jwk.alg() != null ? jwk.alg() : "RS256"; // Default to RS256
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
    private KeyInfo processEcKey(JwkKey jwk, String kid) {
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
    private String determineEcAlgorithm(JwkKey jwk) {
        if (jwk.getAlg().isPresent()) {
            return jwk.alg();
        }

        // Determine algorithm based on curve
        String curve = jwk.crv() != null ? jwk.crv() : "P-256";
        return JwkKeyHandler.determineEcAlgorithm(curve);
    }
}