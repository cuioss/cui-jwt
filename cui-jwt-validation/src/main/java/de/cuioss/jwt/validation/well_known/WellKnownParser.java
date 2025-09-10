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
package de.cuioss.jwt.validation.well_known;

import de.cuioss.jwt.validation.JWTValidationLogMessages;
import de.cuioss.jwt.validation.ParserConfig;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.tools.logging.CuiLogger;
import de.cuioss.tools.net.http.converter.JsonConverter;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import lombok.RequiredArgsConstructor;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Optional;

/**
 * Handles JSON parsing and validation for well-known endpoint discovery.
 * <p>
 * This class is responsible for:
 * <ul>
 *   <li>Parsing JSON discovery documents</li>
 *   <li>Extracting string values from JSON objects</li>
 *   <li>Validating issuer consistency with well-known URLs</li>
 * </ul>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@RequiredArgsConstructor
class WellKnownParser {

    private static final CuiLogger LOGGER = new CuiLogger(WellKnownParser.class);
    private static final String WELL_KNOWN_OPENID_CONFIGURATION = "/.well-known/openid-configuration";

    private final ParserConfig parserConfig;

    /**
     * JSON converter that uses DSL-JSON with security settings.
     * Initialized lazily to avoid circular dependencies during construction.
     */
    private JsonConverter jsonConverter;

    /**
     * Initializes the JSON converter after construction.
     */
    private JsonConverter initJsonConverter() {
        if (jsonConverter == null) {
            // Handle null parserConfig by using default configuration
            ParserConfig actualConfig = parserConfig != null ? parserConfig : ParserConfig.builder().build();
            jsonConverter = actualConfig.getJsonConverter();
        }
        return jsonConverter;
    }

    /**
     * Parses a JSON response string into a JsonObject.
     *
     * @param responseBody The JSON response string to parse
     * @param wellKnownUrl The well-known URL (used for error messages)
     * @return Optional containing the parsed JsonObject or empty on error
     */
    Optional<JsonObject> parseJsonResponse(String responseBody, URL wellKnownUrl) {
        try {
            Optional<JsonValue> result = initJsonConverter().convert(responseBody);
            if (result.isEmpty()) {
                LOGGER.error(JWTValidationLogMessages.ERROR.JSON_PARSE_FAILED.format(wellKnownUrl, "JSON parsing failed"));
                return Optional.empty();
            }

            // Well-known documents must be JSON objects
            if (result.get() instanceof JsonObject jsonObject) {
                return Optional.of(jsonObject);
            } else {
                LOGGER.error(JWTValidationLogMessages.ERROR.JSON_PARSE_FAILED.format(wellKnownUrl, "Well-known document is not a JSON object"));
                return Optional.empty();
            }
        } catch (TokenValidationException e) {
            // Re-throw security exceptions from JsonContentConverter
            throw e;
        } catch (IllegalStateException | IllegalArgumentException | SecurityException e) {
            // Handle specific runtime exceptions that could occur during JSON parsing
            LOGGER.error(e, JWTValidationLogMessages.ERROR.JSON_PARSE_FAILED.format(wellKnownUrl, e.getMessage()));
            return Optional.empty();
        }
    }

    /**
     * Extracts a string value from a JsonObject.
     *
     * @param jsonObject The JsonObject to extract from
     * @param key The key to extract
     * @return An Optional containing the string value, or empty if not found
     */
    Optional<String> getString(JsonObject jsonObject, String key) {
        if (jsonObject.containsKey(key)) {
            try {
                return Optional.of(jsonObject.getString(key));
            } catch (ClassCastException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * Validates that the issuer from the discovery document matches the well-known URL.
     *
     * @param issuerFromDocument The issuer from the discovery document
     * @param wellKnownUrl The well-known URL
     * @return true if validation passes, false otherwise
     */
    boolean validateIssuer(String issuerFromDocument, URL wellKnownUrl) {
        LOGGER.debug(JWTValidationLogMessages.DEBUG.VALIDATING_ISSUER.format(issuerFromDocument, wellKnownUrl));

        URL issuerAsUrl;
        try {
            issuerAsUrl = URI.create(issuerFromDocument).toURL();
        } catch (MalformedURLException | IllegalArgumentException e) {
            LOGGER.error(e, JWTValidationLogMessages.ERROR.ISSUER_URL_MALFORMED.format(issuerFromDocument, e.getMessage()));
            return false;
        }

        String expectedWellKnownPath = determineWellKnownPath(issuerAsUrl);

        boolean schemeMatch = issuerAsUrl.getProtocol().equals(wellKnownUrl.getProtocol());
        boolean hostMatch = issuerAsUrl.getHost().equalsIgnoreCase(wellKnownUrl.getHost());
        int issuerPort = issuerAsUrl.getPort() == -1 ? issuerAsUrl.getDefaultPort() : issuerAsUrl.getPort();
        int wellKnownPort = wellKnownUrl.getPort() == -1 ? wellKnownUrl.getDefaultPort() : wellKnownUrl.getPort();
        boolean portMatch = issuerPort == wellKnownPort;
        boolean pathMatch = wellKnownUrl.getPath().equals(expectedWellKnownPath);

        if (!(schemeMatch && hostMatch && portMatch && pathMatch)) {
            LOGGER.error(JWTValidationLogMessages.ERROR.ISSUER_VALIDATION_FAILED.format(
                    issuerFromDocument, issuerAsUrl.getProtocol(), issuerAsUrl.getHost(),
                    (issuerAsUrl.getPort() != -1 ? ":" + issuerAsUrl.getPort() : ""),
                    (issuerAsUrl.getPath() == null ? "" : issuerAsUrl.getPath()),
                    wellKnownUrl.toString(),
                    expectedWellKnownPath,
                    schemeMatch, hostMatch, portMatch, issuerPort, wellKnownPort, pathMatch, wellKnownUrl.getPath()));
            return false;
        }
        LOGGER.debug(JWTValidationLogMessages.DEBUG.ISSUER_VALIDATION_SUCCESSFUL.format(issuerFromDocument));
        return true;
    }

    private String determineWellKnownPath(URL issuerAsUrl) {
        String expectedWellKnownPath;
        if (issuerAsUrl.getPath() == null || issuerAsUrl.getPath().isEmpty() || "/".equals(issuerAsUrl.getPath())) {
            expectedWellKnownPath = WELL_KNOWN_OPENID_CONFIGURATION;
        } else {
            String issuerPath = issuerAsUrl.getPath();
            if (issuerPath.endsWith("/")) {
                issuerPath = issuerPath.substring(0, issuerPath.length() - 1);
            }
            expectedWellKnownPath = issuerPath + WELL_KNOWN_OPENID_CONFIGURATION;
        }
        return expectedWellKnownPath;
    }
}