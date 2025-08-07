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

import de.cuioss.tools.logging.LogRecord;
import de.cuioss.tools.logging.LogRecordModel;
import lombok.experimental.UtilityClass;

/**
 * Provides logging messages for the cui-jwt-validation module.
 * All messages follow the format: JWTValidation-[identifier]: [message]
 * <p>
 * Implements requirements:
 * <ul>
 *   <li><a href="https://github.com/cuioss/cui-jwt/tree/main/doc/Requirements.adoc#CUI-JWT-7">CUI-JWT-7: Logging</a></li>
 *   <li><a href="https://github.com/cuioss/cui-jwt/tree/main/doc/Requirements.adoc#CUI-JWT-7.1">CUI-JWT-7.1: Structured Logging</a></li>
 *   <li><a href="https://github.com/cuioss/cui-jwt/tree/main/doc/Requirements.adoc#CUI-JWT-7.2">CUI-JWT-7.2: Helpful Error Messages</a></li>
 * </ul>
 * <p>
 * For more detailed information about log messages, see the
 * <a href="https://github.com/cuioss/cui-jwt/tree/main/doc/LogMessages.adoc">Log Messages Documentation</a>
 *
 * @since 1.0
 */
@UtilityClass
public final class JWTValidationLogMessages {

    private static final String PREFIX = "JWTValidation";

    /**
     * Contains debug-level log messages for informational and diagnostic purposes.
     * These messages are typically used for tracing program pipeline and providing
     * detailed information about normal operations.
     */
    @UtilityClass
    public static final class DEBUG {
        // Token creation success events
        public static final LogRecord ACCESS_TOKEN_CREATED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(500)
                .template("Successfully created access token")
                .build();

        public static final LogRecord ID_TOKEN_CREATED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(501)
                .template("Successfully created ID-Token")
                .build();

        public static final LogRecord REFRESH_TOKEN_CREATED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(502)
                .template("Successfully created Refresh-Token")
                .build();

        // WellKnownHandler debug messages
        public static final LogRecord OPTIONAL_URL_FIELD_MISSING = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(503)
                .template("Optional URL field '%s' is missing in discovery document from %s")
                .build();

        public static final LogRecord VALIDATING_ISSUER = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(504)
                .template("Validating issuer: Document issuer='%s', WellKnown URL='%s'")
                .build();

        public static final LogRecord ISSUER_VALIDATION_SUCCESSFUL = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(505)
                .template("Issuer validation successful for %s")
                .build();

        public static final LogRecord ACCESSIBILITY_CHECK_SUCCESSFUL = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(506)
                .template("Accessibility check for %s URL '%s' successful (HTTP %s)")
                .build();

        public static final LogRecord DISCOVERY_DOCUMENT_FETCHED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(507)
                .template("Successfully fetched discovery document: %s")
                .build();

        public static final LogRecord ACCESS_TOKEN_CACHE_HIT = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(508)
                .template("Access token retrieved from cache")
                .build();
    }

    /**
     * Contains error-level log messages for significant problems that require attention.
     * These messages indicate failures that impact functionality but don't necessarily
     * prevent the application from continuing to run.
     */
    @UtilityClass
    public static final class ERROR {
        public static final LogRecord SIGNATURE_VALIDATION_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(200)
                .template("Failed to validate validation signature: %s")
                .build();

        public static final LogRecord JWKS_CONTENT_SIZE_EXCEEDED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(201)
                .template("JWKS content size exceeds maximum allowed size (upperLimit=%s, actual=%s)")
                .build();

        public static final LogRecord JWKS_INVALID_JSON = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(202)
                .template("Failed to parse JWKS JSON: %s")
                .build();

        public static final LogRecord ISSUER_VALIDATION_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(203)
                .template("Issuer validation failed. Document issuer '%s' (normalized to base URL for .well-known: %s://%s%s%s) does not match the .well-known URL '%s'. Expected path for .well-known: '%s'. SchemeMatch=%s, HostMatch=%s, PortMatch=%s (IssuerPort=%s, WellKnownPort=%s), PathMatch=%s (WellKnownPath='%s')")
                .build();

        public static final LogRecord JWKS_LOAD_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(204)
                .template("Failed to load JWKS")
                .build();

        public static final LogRecord WELL_KNOWN_LOAD_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(205)
                .template("Failed to load well-known endpoints from: %s after %s attempts")
                .build();

        // New entries for direct logging conversions
        public static final LogRecord UNSUPPORTED_JWKS_TYPE = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(206)
                .template("Unsupported JwksType for HttpJwksLoader: %s")
                .build();

        public static final LogRecord REQUIRED_URL_FIELD_MISSING = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(207)
                .template("Required URL field '%s' is missing in discovery document from %s")
                .build();

        public static final LogRecord MALFORMED_URL_FIELD = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(208)
                .template("Malformed URL for field '%s': %s from %s - %s")
                .build();

        public static final LogRecord JSON_PARSE_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(209)
                .template("Failed to parse JSON from %s: %s")
                .build();

        public static final LogRecord ISSUER_URL_MALFORMED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(210)
                .template("Issuer URL from discovery document is malformed: %s - %s")
                .build();

        public static final LogRecord CACHE_TOKEN_NO_EXPIRATION = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(211)
                .template("Token passed validation but has no expiration time - this indicates a validation bug")
                .build();

        public static final LogRecord CACHE_TOKEN_STORE_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(212)
                .template("Unexpected error while caching token")
                .build();

        public static final LogRecord CACHE_VALIDATION_FUNCTION_NULL = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(213)
                .template("Validation function returned null instead of throwing exception")
                .build();

        public static final LogRecord CACHE_EVICTION_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(214)
                .template("Error during cache eviction")
                .build();
    }

    /**
     * Contains info-level log messages for general operational information.
     * These messages provide high-level information about the normal operation
     * of the application that is useful for monitoring.
     */
    @UtilityClass
    public static final class INFO {
        public static final LogRecord TOKEN_FACTORY_INITIALIZED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(1)
                .template("TokenValidator initialized with %s")
                .build();

        public static final LogRecord JWKS_KEYS_UPDATED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(2)
                .template("Keys updated due to data change - load state: %s")
                .build();

        public static final LogRecord JWKS_HTTP_LOADED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(3)
                .template("Successfully loaded JWKS from HTTP endpoint")
                .build();

        public static final LogRecord JWKS_BACKGROUND_REFRESH_STARTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(4)
                .template("Background JWKS refresh started with interval: %s seconds")
                .build();

        public static final LogRecord JWKS_BACKGROUND_REFRESH_UPDATED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(5)
                .template("Background JWKS refresh updated keys, load state: %s")
                .build();

        // New entries for direct logging conversions
        public static final LogRecord ISSUER_CONFIG_SKIPPED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(6)
                .template("Skipping disabled issuer configuration %s")
                .build();

        public static final LogRecord HTTP_CONTENT_LOADED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(7)
                .template("Loaded fresh HTTP content from %s")
                .build();

        public static final LogRecord JWKS_URI_RESOLVED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(8)
                .template("Successfully resolved JWKS URI from well-known endpoint: %s")
                .build();

        public static final LogRecord WELL_KNOWN_ENDPOINTS_LOADED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(9)
                .template("Successfully loaded well-known endpoints from: %s")
                .build();
    }

    /**
     * Contains warning-level log messages for potential issues that don't prevent
     * normal operation but may indicate problems. These messages highlight situations
     * that should be monitored or addressed to prevent future errors.
     */
    @UtilityClass
    public static final class WARN {
        public static final LogRecord TOKEN_SIZE_EXCEEDED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(100)
                .template("Token exceeds maximum size limit of %s bytes, validation will be rejected")
                .build();

        public static final LogRecord TOKEN_IS_EMPTY = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(101)
                .template("The given validation was empty, request will be rejected")
                .build();

        public static final LogRecord KEY_NOT_FOUND = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(102)
                .template("No key found with ID: %s")
                .build();

        public static final LogRecord ISSUER_MISMATCH = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(103)
                .template("Token issuer '%s' does not match expected issuer '%s'")
                .build();

        public static final LogRecord JWKS_FETCH_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(104)
                .template("Failed to fetch JWKS: HTTP %s")
                .build();

        public static final LogRecord JWKS_JSON_PARSE_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(105)
                .template("Failed to parse JWKS JSON: %s")
                .build();

        public static final LogRecord FAILED_TO_DECODE_JWT = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(106)
                .template("Failed to decode JWT Token")
                .build();

        public static final LogRecord INVALID_JWT_FORMAT = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(107)
                .template("Invalid JWT Token format: expected 3 parts but got %s")
                .build();

        public static final LogRecord FAILED_TO_DECODE_HEADER = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(108)
                .template("Failed to decode header part")
                .build();

        public static final LogRecord FAILED_TO_DECODE_PAYLOAD = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(109)
                .template("Failed to decode payload part")
                .build();

        public static final LogRecord DECODED_PART_SIZE_EXCEEDED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(110)
                .template("Decoded part exceeds maximum size limit of %s bytes")
                .build();

        public static final LogRecord UNSUPPORTED_ALGORITHM = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(111)
                .template("Unsupported algorithm: %s")
                .build();

        public static final LogRecord JWKS_MISSING_KEYS = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(112)
                .template("JWKS JSON does not contain 'keys' array or 'kty' field")
                .build();

        public static final LogRecord TOKEN_NBF_FUTURE = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(113)
                .template("Token has a 'not before' claim that is more than 60 seconds in the future")
                .build();

        public static final LogRecord UNKNOWN_TOKEN_TYPE = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(114)
                .template("Unknown validation type: %s")
                .build();

        public static final LogRecord FAILED_TO_READ_JWKS_FILE = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(115)
                .template("Failed to read JWKS from file: %s")
                .build();

        public static final LogRecord MISSING_CLAIM = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(116)
                .template("Token is missing required claim: %s")
                .build();

        public static final LogRecord TOKEN_EXPIRED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(117)
                .template("Token has expired")
                .build();

        public static final LogRecord AZP_MISMATCH = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(118)
                .template("Token authorized party '%s' does not match expected client ID '%s'")
                .build();

        public static final LogRecord MISSING_RECOMMENDED_ELEMENT = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(119)
                .template("Missing recommended element: %s")
                .build();

        public static final LogRecord AUDIENCE_MISMATCH = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(120)
                .template("Token audience %s does not match any of the expected audiences %s")
                .build();

        public static final LogRecord NO_ISSUER_CONFIG = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(121)
                .template("No configuration found for issuer: %s")
                .build();

        public static final LogRecord INVALID_BASE64_CONTENT = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(122)
                .template("Given contentKey '%s' does not resolve to a non base64 encoded String, actual content = %s")
                .build();

        public static final LogRecord ALGORITHM_REJECTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(123)
                .template("Algorithm %s is explicitly rejected for security reasons")
                .build();

        public static final LogRecord KEY_ROTATION_DETECTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(124)
                .template("Key rotation detected: JWKS content has changed")
                .build();

        public static final LogRecord ACCESSIBILITY_CHECK_HTTP_ERROR = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(125)
                .template("Accessibility check for %s URL '%s' returned HTTP status %s. It might be inaccessible.")
                .build();

        public static final LogRecord INVALID_JWKS_URI = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(126)
                .template("Creating HttpJwksLoaderConfig with invalid JWKS URI. The loader will return empty results.")
                .build();

        public static final LogRecord JWKS_LOAD_FAILED_CACHED_CONTENT = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(127)
                .template("Load operation failed but using cached content")
                .build();

        public static final LogRecord JWKS_LOAD_FAILED_NO_CACHE = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(128)
                .template("Load operation failed with no cached content available")
                .build();

        public static final LogRecord JWK_KEY_MISSING_KTY = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(129)
                .template("Key missing required 'kty' parameter")
                .build();

        public static final LogRecord JWK_UNSUPPORTED_KEY_TYPE = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(130)
                .template("Unsupported key type: %s")
                .build();

        public static final LogRecord JWK_KEY_ID_TOO_LONG = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(131)
                .template("Key ID exceeds maximum length: %s")
                .build();

        public static final LogRecord JWK_INVALID_ALGORITHM = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(132)
                .template("Invalid or unsupported algorithm: %s")
                .build();

        // New entries for direct logging conversions
        public static final LogRecord ISSUER_CONFIG_UNHEALTHY = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(133)
                .template("Found unhealthy issuer config: %s")
                .build();

        public static final LogRecord BACKGROUND_REFRESH_SKIPPED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(134)
                .template("Background refresh skipped - no HTTP cache available")
                .build();

        public static final LogRecord BACKGROUND_REFRESH_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(135)
                .template("Background JWKS refresh failed: %s")
                .build();

        public static final LogRecord JWKS_URI_RESOLUTION_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(136)
                .template("Failed to resolve JWKS URI from well-known resolver")
                .build();

        public static final LogRecord HTTP_STATUS_WARNING = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(137)
                .template("HTTP %s (%s) from %s")
                .build();

        public static final LogRecord HTTP_FETCH_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(138)
                .template("Failed to fetch HTTP content from %s")
                .build();

        public static final LogRecord HTTP_FETCH_INTERRUPTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(139)
                .template("Interrupted while fetching HTTP content from %s")
                .build();

        public static final LogRecord JWKS_OBJECT_NULL = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(140)
                .template("JWKS object is null")
                .build();

        public static final LogRecord JWKS_EXCESSIVE_PROPERTIES = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(141)
                .template("JWKS object has excessive number of properties: %s")
                .build();

        public static final LogRecord JWKS_KEYS_ARRAY_TOO_LARGE = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(142)
                .template("JWKS keys array exceeds maximum size: %s")
                .build();

        public static final LogRecord JWKS_KEYS_ARRAY_EMPTY = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(143)
                .template("JWKS keys array is empty")
                .build();

        public static final LogRecord JWK_MISSING_KTY = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(144)
                .template("JWK is missing required field 'kty'")
                .build();

        public static final LogRecord RSA_KEY_PARSE_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(145)
                .template("Failed to parse RSA key with ID %s: %s")
                .build();

        public static final LogRecord EC_KEY_PARSE_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(146)
                .template("Failed to parse EC key with ID %s: %s")
                .build();
    }

}
