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
package de.cuioss.jwt.validation;

import jakarta.json.Json;
import jakarta.json.JsonReaderFactory;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration class for the TokenValidator.
 * <p>
 * This class provides configuration options for the TokenValidator, such as
 * maximum token size and maximum payload size.
 * It also includes JSON parsing security settings like maximum string size,
 * maximum array size, and maximum depth.
 * <p>
 * <strong>Security Layers:</strong>
 * The configuration provides multiple layers of protection against various attack vectors:
 * <ul>
 *   <li><strong>maxTokenSize</strong>: Limits the entire JWT token string before any processing.
 *       This prevents oversized tokens from consuming memory or processing time.</li>
 *   <li><strong>maxPayloadSize</strong>: Limits each decoded JWT part (header, payload) after Base64 decoding.
 *       Since Base64 encoding increases size by ~33%, decoded parts are smaller than the original token.</li>
 *   <li><strong>maxStringSize</strong>: Limits individual JSON string values within the decoded parts.
 *       This prevents JSON parsing attacks where individual fields contain extremely large strings.</li>
 * </ul>
 * <p>
 * <strong>Default Size Relationships:</strong>
 * The defaults are designed with logical size relationships:
 * <ul>
 *   <li>maxTokenSize (8KB) - allows for OAuth 2.0 BCP recommended size</li>
 *   <li>maxPayloadSize (8KB) - allows for larger JWT payloads with extensive claims</li>
 *   <li>maxStringSize (4KB) - prevents individual fields from dominating the payload</li>
 * </ul>
 * <p>
 * This class is immutable and thread-safe.
 * <p>
 * Usage example:
 * <pre>
 * ParserConfig config = ParserConfig.builder()
 *     .maxTokenSize(16 * 1024)
 *     .maxPayloadSize(4 * 1024)
 *     .maxStringSize(2 * 1024)
 *     .build();
 * </pre>
 * <p>
 * Implements requirements:
 * <ul>
 *   <li><a href="https://github.com/cuioss/cui-jwt/tree/main/doc/Requirements.adoc#CUI-JWT-8.1">CUI-JWT-8.1: Token Size Limits</a></li>
 *   <li><a href="https://github.com/cuioss/cui-jwt/tree/main/doc/Requirements.adoc#CUI-JWT-8.2">CUI-JWT-8.2: Safe Parsing</a></li>
 * </ul>
 * <p>
 * For more detailed specifications, see the
 * <a href="https://github.com/cuioss/cui-jwt/tree/main/doc/specification/token-size-validation.adoc">Token Size Validation Specification</a>
 *
 * @since 1.0
 */
@Builder
@Value
public class ParserConfig {

    /**
     * Default maximum size of a JWT token in bytes to prevent overflow attacks.
     * 8KB as recommended by OAuth 2.0 JWT BCP Section 3.11.
     * This is the first line of defense, checking the entire token string before any processing.
     */
    public static final int DEFAULT_MAX_TOKEN_SIZE = 8 * 1024;

    /**
     * Default maximum size of decoded JSON payload in bytes.
     * 8KB per part allows for larger JWT payloads while still providing
     * protection against memory exhaustion attacks. This accommodates tokens
     * with extensive claims or embedded data.
     */
    public static final int DEFAULT_MAX_PAYLOAD_SIZE = 8 * 1024;

    /**
     * Default maximum string size for individual JSON string values.
     * 4KB prevents any single JSON string field from dominating the payload size,
     * providing protection against JSON parsing attacks where individual fields
     * contain extremely large strings.
     */
    public static final int DEFAULT_MAX_STRING_SIZE = 4 * 1024;

    /**
     * Default maximum array size for JSON parsing.
     */
    public static final int DEFAULT_MAX_ARRAY_SIZE = 64;

    /**
     * Default maximum depth for JSON parsing.
     */
    public static final int DEFAULT_MAX_DEPTH = 10;


    /**
     * Maximum size of a JWT token in bytes to prevent overflow attacks.
     * This limit is applied to the entire token string before any processing begins.
     * Protects against denial-of-service attacks via extremely large token strings.
     */
    @Builder.Default
    int maxTokenSize = DEFAULT_MAX_TOKEN_SIZE;

    /**
     * Maximum size of decoded JSON payload in bytes.
     * This limit is applied to each Base64-decoded JWT part (header, payload).
     * Since Base64 encoding increases size by ~33%, decoded parts are smaller than the original token.
     */
    @Builder.Default
    int maxPayloadSize = DEFAULT_MAX_PAYLOAD_SIZE;

    /**
     * Maximum string size for individual JSON string values during parsing.
     * This limit is applied by the JSON parser to individual string fields within the JWT parts.
     * Prevents JSON parsing attacks where individual fields contain extremely large strings.
     */
    @Builder.Default
    int maxStringSize = DEFAULT_MAX_STRING_SIZE;

    /**
     * Maximum array size for JSON parsing.
     */
    @Builder.Default
    int maxArraySize = DEFAULT_MAX_ARRAY_SIZE;

    /**
     * Maximum depth for JSON parsing.
     */
    @Builder.Default
    int maxDepth = DEFAULT_MAX_DEPTH;


    /**
     * Cached JsonReaderFactory with security settings.
     * This is lazily initialized to avoid unnecessary creation.
     */
    @Getter(lazy = true)
    JsonReaderFactory jsonReaderFactory = createJsonReaderFactory();

    /**
     * Creates a JsonReaderFactory with security settings.
     * This method is used by the lazy getter for jsonReaderFactory.
     *
     * @return a JsonReaderFactory configured with security settings
     */
    private JsonReaderFactory createJsonReaderFactory() {
        Map<String, Object> config = new HashMap<>();
        // Use the correct property names for Jakarta JSON API
        config.put("jakarta.json.stream.maxStringLength", maxStringSize);
        config.put("jakarta.json.stream.maxArraySize", maxArraySize);
        config.put("jakarta.json.stream.maxDepth", maxDepth);
        return Json.createReaderFactory(config);
    }
}
