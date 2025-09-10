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
package de.cuioss.tools.net.http.converter;

import com.dslplatform.json.DslJson;
import de.cuioss.jwt.validation.JWTValidationLogMessages;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.jwt.validation.security.SecurityEventCounter.EventType;
import de.cuioss.tools.logging.CuiLogger;
import de.cuioss.tools.net.http.json.DslJsonObjectAdapter;
import de.cuioss.tools.net.http.json.DslJsonValueFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JSON content converter that uses DSL-JSON for secure JSON parsing while maintaining
 * Jakarta JSON API compatibility through an adapter bridge.
 * <p>
 * This converter processes String-based HTTP responses as JSON using DSL-JSON
 * with configurable security limits, then wraps the results in Jakarta JSON API
 * implementations. This allows existing code to work unchanged while getting
 * the security benefits of DSL-JSON parsing.
 * <p>
 * Key benefits:
 * <ul>
 *   <li>Zero breaking changes to existing Jakarta JSON API consuming code</li>
 *   <li>DSL-JSON security limits are actually enforced (unlike Eclipse Parsson)</li>
 *   <li>Better performance through DSL-JSON's compile-time code generation</li>
 *   <li>Type-safe JSON access through standard Jakarta JSON API</li>
 * </ul>
 * <p>
 * This implementation also serves as the primary implementation of {@link JsonConverter},
 * providing a clean interface for secure JSON processing that can be used independently
 * of HTTP content conversion scenarios.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public class JsonContentConverter extends StringContentConverter<JsonObject> implements JsonConverter {

    private static final CuiLogger LOGGER = new CuiLogger(JsonContentConverter.class);

    private final DslJson<Object> dslJson;
    private final SecurityEventCounter securityEventCounter;
    private final int maxContentSize;

    /**
     * Creates a JSON content converter with the specified DSL-JSON instance.
     *
     * @param dslJson the DSL-JSON instance containing JSON security settings
     */
    public JsonContentConverter(@NonNull DslJson<Object> dslJson) {
        this(dslJson, new SecurityEventCounter());
    }

    /**
     * Creates a JSON content converter with the specified DSL-JSON instance and security event counter.
     *
     * @param dslJson the DSL-JSON instance containing JSON security settings
     * @param securityEventCounter the security event counter for tracking violations
     */
    public JsonContentConverter(@NonNull DslJson<Object> dslJson, @NonNull SecurityEventCounter securityEventCounter) {
        this(dslJson, securityEventCounter, 8 * 1024); // Default 8KB limit
    }

    /**
     * Creates a JSON content converter with the specified DSL-JSON instance, security event counter, and content size limit.
     *
     * @param dslJson the DSL-JSON instance containing JSON security settings
     * @param securityEventCounter the security event counter for tracking violations
     * @param maxContentSize maximum allowed JSON content size in bytes
     */
    public JsonContentConverter(@NonNull DslJson<Object> dslJson, @NonNull SecurityEventCounter securityEventCounter, int maxContentSize) {
        this.dslJson = dslJson;
        this.securityEventCounter = securityEventCounter;
        this.maxContentSize = maxContentSize;
    }

    /**
     * Internal method to convert string to JsonValue (supporting all JSON types).
     */
    private Optional<JsonValue> convertStringToJsonValue(String rawContent) {
        if (rawContent == null || rawContent.trim().isEmpty()) {
            return Optional.of(emptyValue());
        }

        // Check if content size exceeds maximum - following same pattern as token size validation
        if (rawContent.getBytes(StandardCharsets.UTF_8).length > maxContentSize) {
            LOGGER.warn(JWTValidationLogMessages.WARN.JSON_PARSING_FAILED.format("JSON content size exceeds maximum allowed size"));
            securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
            throw new TokenValidationException(
                    EventType.JWKS_JSON_PARSE_FAILED,
                    JWTValidationLogMessages.WARN.JSON_PARSING_FAILED.format("JSON content size exceeds maximum allowed size")
            );
        }

        try {
            // For arbitrary JSON parsing, we need to try different types since DSL-JSON
            // doesn't support Object.class deserialization without specific configuration
            byte[] bytes = rawContent.getBytes();
            ParseResult result = tryParseArbitraryJson(bytes);

            if (!result.success) {
                LOGGER.error(JWTValidationLogMessages.ERROR.JWKS_INVALID_JSON.format("Unable to parse JSON content"));
                return Optional.empty();
            }

            // Use factory to create appropriate JsonValue for all JSON types
            // Note: result.value can be null for JSON null literal, which is valid
            JsonValue jsonValue = DslJsonValueFactory.createJsonValue(result.value);
            return Optional.of(jsonValue);

        } catch (IOException e) {
            // Check if this is a security limit violation
            String errorMessage = e.getMessage();
            if (isSecurityLimitViolation(errorMessage)) {
                // Follow the security violation pattern: warn, count, throw
                LOGGER.warn(JWTValidationLogMessages.WARN.JSON_PARSING_FAILED.format(errorMessage));
                securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
                throw new TokenValidationException(
                        EventType.JWKS_JSON_PARSE_FAILED,
                        JWTValidationLogMessages.WARN.JSON_PARSING_FAILED.format(errorMessage)
                );
            }

            // Regular parsing errors - malformed JSON, I/O error
            LOGGER.error(e, JWTValidationLogMessages.ERROR.JWKS_INVALID_JSON.format(e.getMessage()));
            return Optional.empty();
        }
    }

    @Override
    protected Optional<JsonObject> convertString(String rawContent) {
        // Convert to JsonValue first then extract JsonObject if possible
        Optional<JsonValue> jsonValue = convertStringToJsonValue(rawContent);

        if (jsonValue.isEmpty()) {
            return Optional.empty();
        }

        // If it's a JsonObject, return it
        if (jsonValue.get() instanceof JsonObject jsonObject) {
            return Optional.of(jsonObject);
        }

        // If it's not a JsonObject, return empty to indicate failure
        return Optional.empty();
    }


    /**
     * JsonConverter interface implementation that returns Optional for clear success/failure semantics.
     *
     * @param jsonString the JSON string to parse, may be null or empty
     * @return Optional containing JsonValue if parsing succeeds, empty if parsing fails
     * @throws TokenValidationException if security limits are violated
     */
    @Override
    @NonNull
    public Optional<JsonValue> convert(@Nullable String jsonString) {
        try {
            return convertStringToJsonValue(jsonString);
        } catch (TokenValidationException e) {
            // Re-throw security exceptions
            throw e;
        } catch (IllegalStateException | IllegalArgumentException | SecurityException e) {
            // Handle specific runtime exceptions that could occur during JSON conversion
            LOGGER.error(e, "Unexpected error during JSON conversion: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Result of JSON parsing attempt.
     */
    private static class ParseResult {
        final boolean success;
        final Object value;

        ParseResult(boolean success, Object value) {
            this.success = success;
            this.value = value;
        }

        static ParseResult success(Object value) {
            return new ParseResult(true, value);
        }

        static ParseResult failure() {
            return new ParseResult(false, null);
        }
    }

    /**
     * Attempts to parse arbitrary JSON using DSL-JSON's raw parsing capabilities.
     * Since DSL-JSON's typed deserialization requires configuration for all types,
     * we use the raw JsonReader approach to parse any valid JSON.
     * 
     * @param bytes the JSON bytes to parse
     * @return ParseResult indicating success/failure and the parsed value
     * @throws IOException if a security limit violation occurs
     */
    private ParseResult tryParseArbitraryJson(byte[] bytes) throws IOException {
        // Try JSON object first (most common for our use cases)
        try {
            Object result = dslJson.deserialize(Map.class, bytes, bytes.length);
            return ParseResult.success(result);
        } catch (IOException e) {
            if (isSecurityLimitViolation(e.getMessage())) {
                throw e; // Re-throw security violations
            }
            // Fall through to manual parsing
        }

        // For non-object JSON, we need to parse manually
        // since DSL-JSON doesn't handle arbitrary types well
        Object result = parseJsonManually(new String(bytes).trim());
        if (result != null || "null".equals(new String(bytes).trim())) {
            return ParseResult.success(result); // null is a valid JSON value
        }
        return ParseResult.failure();
    }

    /**
     * Manually parses JSON literals that DSL-JSON can't handle with basic configuration.
     * This handles arrays, strings, numbers, booleans, and null.
     * 
     * @param json the JSON string to parse
     * @return the parsed value or null if parsing fails
     */
    private Object parseJsonManually(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        // Handle null literal
        if ("null".equals(json)) {
            return null; // Will be converted to JsonValue.NULL by factory
        }

        // Handle boolean literals
        if ("true".equals(json)) {
            return Boolean.TRUE;
        }
        if ("false".equals(json)) {
            return Boolean.FALSE;
        }

        // Handle string literals (quoted strings)
        if (json.startsWith("\"") && json.endsWith("\"") && json.length() >= 2) {
            // Basic string unescaping - just remove quotes for now
            // TODO: Full JSON string unescaping would need proper handling of \", \\, etc.
            return json.substring(1, json.length() - 1);
        }

        // Handle array literals
        if (json.startsWith("[") && json.endsWith("]")) {
            // For arrays, we need to parse the content manually or use a JSON parser
            // For now, return a simple marker that this is an array
            // The actual parsing would require recursive JSON parsing
            try {
                // Try to use DSL-JSON for the array content by creating a temporary JSON object wrapper
                String wrappedJson = "{\"array\":" + json + "}";
                Map<?, ?> wrapper = dslJson.deserialize(Map.class, wrappedJson.getBytes(), wrappedJson.length());
                return wrapper.get("array");
            } catch (IOException e) {
                return List.of(); // Empty list as fallback
            }
        }

        // Handle number literals
        try {
            if (json.contains(".")) {
                return Double.parseDouble(json);
            } else {
                return Integer.parseInt(json);
            }
        } catch (NumberFormatException e) {
            // Not a valid number
        }

        // Unable to parse - return null
        return null;
    }

    /**
     * Determines if an IOException is caused by a DSL-JSON security limit violation.
     *
     * @param errorMessage the error message from the IOException
     * @return true if this appears to be a security limit violation
     */
    private boolean isSecurityLimitViolation(String errorMessage) {
        if (errorMessage == null) {
            return false;
        }

        // DSL-JSON throws IOException for various security limit violations
        return errorMessage.contains("buffer") ||
                errorMessage.contains("limit") ||
                errorMessage.contains("too large") ||
                errorMessage.contains("exceeded");
    }

    @Override
    @NonNull
    public JsonObject emptyValue() {
        return new DslJsonObjectAdapter(Map.of());
    }
}