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

import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Interface for secure JSON conversion with Jakarta JSON API compatibility.
 * <p>
 * This interface defines the contract for JSON processing components that need to:
 * <ul>
 *   <li>Convert JSON strings to Jakarta JSON API values safely</li>
 *   <li>Handle parsing errors gracefully</li>
 *   <li>Enforce security limits during parsing</li>
 *   <li>Support all JSON value types (objects, arrays, primitives)</li>
 *   <li>Provide consistent empty value handling</li>
 * </ul>
 * <p>
 * Implementations are expected to:
 * <ul>
 *   <li>Never return null from {@link #convert(String)} - always return Optional</li>
 *   <li>Return empty Optional for invalid/unparseable JSON</li>
 *   <li>Return Optional.of(jsonValue) for successful parsing of any valid JSON</li>
 *   <li>Apply appropriate security limits (string length, depth, buffer size)</li>
 *   <li>Log parsing errors appropriately</li>
 *   <li>Be thread-safe</li>
 * </ul>
 * <p>
 * This interface enables decoupling between JSON processing configuration (like ParserConfig)
 * and the actual JSON conversion implementation, supporting architectural separation where
 * configuration and implementation reside in different modules.
 * <p>
 * Example usage:
 * <pre>
 * JsonConverter converter = parserConfig.getJsonConverter();
 * Optional&lt;JsonValue&gt; result = converter.convert("{\"key\":\"value\"}");
 * 
 * // Handle invalid JSON gracefully  
 * Optional&lt;JsonValue&gt; invalid = converter.convert("invalid json"); // Returns empty Optional
 * assertTrue(invalid.isEmpty());
 * 
 * // Handle valid JSON of any type
 * Optional&lt;JsonValue&gt; jsonObj = converter.convert("{}"); // JSON object
 * Optional&lt;JsonValue&gt; jsonArr = converter.convert("[1,2,3]"); // JSON array  
 * Optional&lt;JsonValue&gt; jsonStr = converter.convert("\"hello\""); // JSON string
 * assertTrue(jsonObj.isPresent() &amp;&amp; jsonArr.isPresent() &amp;&amp; jsonStr.isPresent());
 * 
 * // Get empty value for initialization/default cases
 * JsonObject defaultValue = converter.emptyValue();
 * </pre>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public interface JsonConverter {

    /**
     * Converts a JSON string to a JsonValue with security limits enforced.
     * <p>
     * This method performs secure JSON parsing with the following guarantees:
     * <ul>
     *   <li>Never returns null - always returns an Optional instance</li>
     *   <li>Returns empty Optional for null, empty, or invalid JSON input</li>
     *   <li>Returns Optional.of(jsonValue) for valid JSON of any type (objects, arrays, primitives)</li>
     *   <li>Enforces security limits (string length, buffer size, nesting depth)</li>
     *   <li>Logs parsing errors appropriately without exposing sensitive details</li>
     *   <li>Throws TokenValidationException for security limit violations</li>
     * </ul>
     * <p>
     * Security considerations:
     * <ul>
     *   <li>Input size limits are enforced to prevent memory exhaustion</li>
     *   <li>JSON structure limits prevent deeply nested attacks</li>
     *   <li>String length limits prevent individual field attacks</li>
     *   <li>Buffer size limits prevent parser buffer overflows</li>
     * </ul>
     *
     * @param jsonString the JSON string to parse, may be null or empty
     * @return Optional containing JsonValue if parsing succeeds, empty if parsing fails
     * @throws de.cuioss.jwt.validation.exception.TokenValidationException if security limits are violated
     */
    @NonNull
    Optional<JsonValue> convert(@Nullable String jsonString);

    /**
     * Returns an empty JsonObject for use as default/fallback value.
     * <p>
     * This method provides a consistent way to obtain empty JsonObject instances
     * that match the behavior of failed conversions. The returned object:
     * <ul>
     *   <li>Is never null</li>
     *   <li>Has isEmpty() == true</li>
     *   <li>Is safe to use in all JsonObject operations</li>
     *   <li>May be a singleton or new instance (implementation dependent)</li>
     * </ul>
     *
     * @return an empty JsonObject, never null
     */
    @NonNull
    JsonObject emptyValue();
}