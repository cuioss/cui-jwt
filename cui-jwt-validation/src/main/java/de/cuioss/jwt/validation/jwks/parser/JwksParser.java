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

import de.cuioss.jwt.validation.JWTValidationLogMessages;
import de.cuioss.jwt.validation.ParserConfig;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.jwks.key.JwkKeyConstants;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.jwt.validation.security.SecurityEventCounter.EventType;
import de.cuioss.tools.logging.CuiLogger;
import de.cuioss.tools.net.http.converter.JsonConverter;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parses and validates JWKS content, extracting individual JWK objects.
 * This class is responsible for:
 * <ul>
 *   <li>Parsing JSON content with security limits</li>
 *   <li>Validating JWKS structure and constraints</li>
 *   <li>Extracting keys from JWKS structure</li>
 *   <li>Handling both standard JWKS format and single key format</li>
 *   <li>Security event tracking for parsing failures</li>
 * </ul>
 */
@RequiredArgsConstructor
public class JwksParser {

    private static final CuiLogger LOGGER = new CuiLogger(JwksParser.class);

    @NonNull
    private final ParserConfig parserConfig;

    @NonNull
    private final SecurityEventCounter securityEventCounter;

    private final JsonConverter jsonConverter;

    /**
     * Create JwksParser with ParserConfig and SecurityEventCounter.
     * JsonConverter will be initialized lazily.
     */
    public JwksParser(@NonNull ParserConfig parserConfig, @NonNull SecurityEventCounter securityEventCounter) {
        this.parserConfig = parserConfig;
        this.securityEventCounter = securityEventCounter;
        this.jsonConverter = null;
    }

    private JsonConverter initJsonConverter() {
        if (jsonConverter == null) {
            return parserConfig.getJsonConverter();
        }
        return jsonConverter;
    }

    /**
     * Parse JWKS content and extract individual JWK objects.
     * 
     * @param jwksContent the JWKS content as a string
     * @return a list of parsed JWK objects, empty if parsing fails
     */
    public List<JsonObject> parse(String jwksContent) {
        List<JsonObject> result = new ArrayList<>();

        // Check content size
        if (!validateContentSize(jwksContent)) {
            return result;
        }

        try {
            // Use JsonContentConverter with DSL-JSON security settings
            Optional<JsonValue> parseResult = initJsonConverter().convert(jwksContent);

            // Check if parsing failed (empty Optional indicates parse failure)
            if (parseResult.isEmpty()) {
                LOGGER.error(JWTValidationLogMessages.ERROR.JWKS_INVALID_JSON.format("JSON parsing returned empty result"));
                securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
                return result;
            }

            // JWKS documents must be JSON objects
            if (parseResult.get() instanceof JsonObject jsonObject) {
                return parseJsonObject(jsonObject);
            } else {
                LOGGER.error(JWTValidationLogMessages.ERROR.JWKS_INVALID_JSON.format("JWKS document is not a JSON object"));
                securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
                return result;
            }
        } catch (TokenValidationException e) {
            // Re-throw security exceptions
            throw e;
        } catch (IllegalStateException | IllegalArgumentException | SecurityException e) {
            // Handle specific runtime exceptions that could occur during JSON parsing
            LOGGER.error(e, JWTValidationLogMessages.ERROR.JWKS_INVALID_JSON.format(e.getMessage()));
            securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
        }

        return result;
    }

    /**
     * Parse JWKS content directly from JsonObject and extract individual JWK objects.
     * <p>
     * This method provides optimal performance for JWKS content that has already been
     * parsed from HTTP responses using JsonContentConverter, eliminating the need
     * for double JSON parsing.
     * 
     * @param jwks the JWKS content as a JsonObject
     * @return a list of parsed JWK objects, empty if parsing fails
     */
    public List<JsonObject> parse(JsonObject jwks) {
        if (jwks == null) {
            LOGGER.error(JWTValidationLogMessages.ERROR.JWKS_INVALID_JSON.format("JWKS JsonObject is null"));
            return new ArrayList<>();
        }
        return parseJsonObject(jwks);
    }

    /**
     * Internal method to parse a JsonObject into individual JWK objects.
     * 
     * @param jwks the JWKS JsonObject to parse
     * @return a list of parsed JWK objects
     */
    private List<JsonObject> parseJsonObject(JsonObject jwks) {
        List<JsonObject> result = new ArrayList<>();
        extractKeys(jwks, result);
        return result;
    }

    /**
     * Validate JWKS content size to prevent memory exhaustion attacks.
     * 
     * @param jwksContent the JWKS content
     * @return true if content size is within limits, false otherwise
     */
    private boolean validateContentSize(String jwksContent) {
        int actualSize = jwksContent.getBytes(StandardCharsets.UTF_8).length;
        int upperLimit = parserConfig.getMaxPayloadSize();

        if (actualSize > upperLimit) {
            LOGGER.error(JWTValidationLogMessages.ERROR.JWKS_CONTENT_SIZE_EXCEEDED.format(upperLimit, actualSize));
            securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
            return false;
        }

        return true;
    }

    /**
     * Extract keys from a JWKS object with validation.
     * Handles both standard JWKS format (with "keys" array) and single key format.
     * 
     * @param jwks the JWKS object
     * @param result the list to store extracted keys
     */
    private void extractKeys(JsonObject jwks, List<JsonObject> result) {
        // Validate JWKS structure first
        if (!validateJwksStructure(jwks)) {
            return;
        }

        // Check if this is a JWKS with a "keys" array or a single key
        if (JwkKeyConstants.Keys.isPresent(jwks)) {
            extractKeysFromArray(jwks, result);
        } else if (JwkKeyConstants.KeyType.isPresent(jwks)) {
            // This is a single key object
            result.add(jwks);
        } else {
            LOGGER.warn(JWTValidationLogMessages.WARN.JWKS_MISSING_KEYS::format);
            securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
        }
    }

    /**
     * Validates the structure and content of a JWKS object.
     * 
     * @param jwks the JWKS object to validate
     * @return true if the JWKS structure is valid, false otherwise
     */
    private boolean validateJwksStructure(JsonObject jwks) {
        // Basic null check
        if (jwks == null) {
            LOGGER.warn(JWTValidationLogMessages.WARN.JWKS_OBJECT_NULL::format);
            securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
            return false;
        }

        // Check for excessive number of top-level properties
        if (jwks.size() > 10) {
            LOGGER.warn(JWTValidationLogMessages.WARN.JWKS_EXCESSIVE_PROPERTIES.format(jwks.size()));
            securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
            return false;
        }

        // If it has a "keys" array, validate it
        if (JwkKeyConstants.Keys.isPresent(jwks)) {
            JsonArray keysArray = jwks.getJsonArray(JwkKeyConstants.Keys.KEY);

            // Check array size limits
            if (keysArray.size() > 50) {
                LOGGER.warn(JWTValidationLogMessages.WARN.JWKS_KEYS_ARRAY_TOO_LARGE.format(keysArray.size()));
                securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
                return false;
            }

            if (keysArray.isEmpty()) {
                LOGGER.warn(JWTValidationLogMessages.WARN.JWKS_KEYS_ARRAY_EMPTY::format);
                securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
                return false;
            }
        }

        return true;
    }

    /**
     * Extract keys from a standard JWKS with "keys" array.
     * 
     * @param jwks the JWKS object
     * @param result the list to store extracted keys
     */
    private void extractKeysFromArray(JsonObject jwks, List<JsonObject> result) {
        var keysArray = JwkKeyConstants.Keys.extract(jwks);
        if (keysArray.isPresent()) {
            JsonArray array = keysArray.get();
            for (int i = 0; i < array.size(); i++) {
                result.add(array.getJsonObject(i));
            }
        }
    }
}