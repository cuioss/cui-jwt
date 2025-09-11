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

import com.dslplatform.json.DslJson;
import de.cuioss.jwt.validation.JWTValidationLogMessages;
import de.cuioss.jwt.validation.ParserConfig;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.jwt.validation.security.SecurityEventCounter.EventType;
import de.cuioss.tools.logging.CuiLogger;
import jakarta.json.JsonObject;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Handles DSL-JSON mapping and validation for well-known endpoint discovery.
 * <p>
 * This class leverages DSL-JSON's compile-time code generation to directly
 * map well-known discovery documents to type-safe Java records, eliminating
 * the need for generic JSON object parsing and providing better performance.
 * <p>
 * Key features:
 * <ul>
 *   <li>Direct mapping to {@link WellKnownConfiguration} records</li>
 *   <li>Compile-time code generation for native compilation support</li>
 *   <li>Enforced security limits through DSL-JSON configuration</li>
 *   <li>Structured access to well-known discovery fields</li>
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
    private final SecurityEventCounter securityEventCounter;
    private final int maxContentSize;

    /**
     * DSL-JSON instance for direct mapping to WellKnownConfiguration.
     * Initialized lazily to avoid circular dependencies during construction.
     */
    private DslJson<Object> dslJson;

    /**
     * Constructor with default security settings.
     */
    WellKnownParser(@Nullable ParserConfig parserConfig) {
        this(parserConfig, new SecurityEventCounter(), 8 * 1024); // Default 8KB limit
    }

    /**
     * Initializes the DSL-JSON instance after construction.
     */
    private DslJson<Object> initDslJson() {
        if (dslJson == null) {
            // Handle null parserConfig by using default configuration
            ParserConfig actualConfig = parserConfig != null ? parserConfig : ParserConfig.builder().build();
            dslJson = actualConfig.getDslJson();
        }
        return dslJson;
    }

    /**
     * Parses a JSON response string directly into a WellKnownConfiguration using DSL-JSON mapping.
     * <p>
     * This method leverages DSL-JSON's compile-time code generation to deserialize
     * the JSON directly into a type-safe record, avoiding generic JSON object parsing.
     *
     * @param responseBody The JSON response string to parse
     * @param wellKnownUrl The well-known URL (used for error messages)
     * @return Optional containing the parsed WellKnownConfiguration or empty on error
     * @throws TokenValidationException if security limits are violated
     */
    Optional<de.cuioss.jwt.validation.json.WellKnownConfiguration> parseWellKnownResponse(@NonNull String responseBody, @NonNull URL wellKnownUrl) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            LOGGER.error(JWTValidationLogMessages.ERROR.JSON_PARSE_FAILED.format(wellKnownUrl, "Empty response body"));
            return Optional.empty();
        }

        // Check content size limit
        byte[] contentBytes = responseBody.getBytes(StandardCharsets.UTF_8);
        if (contentBytes.length > maxContentSize) {
            LOGGER.warn(JWTValidationLogMessages.WARN.JSON_PARSING_FAILED.format("Well-known response size exceeds maximum allowed size"));
            securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
            throw new TokenValidationException(
                    EventType.JWKS_JSON_PARSE_FAILED,
                    JWTValidationLogMessages.WARN.JSON_PARSING_FAILED.format("Well-known response size exceeds maximum allowed size")
            );
        }

        try {
            // Direct deserialization to WellKnownConfiguration using compile-time generated code
            WellKnownConfiguration config = initDslJson().deserialize(WellKnownConfiguration.class, contentBytes, contentBytes.length);

            if (config == null) {
                LOGGER.error(JWTValidationLogMessages.ERROR.JSON_PARSE_FAILED.format(wellKnownUrl, "Failed to deserialize to WellKnownConfiguration"));
                return Optional.empty();
            }

            return Optional.of(config);

        } catch (IOException e) {
            // Check if this is a security limit violation
            String errorMessage = e.getMessage();
            if (isSecurityLimitViolation(errorMessage)) {
                LOGGER.warn(JWTValidationLogMessages.WARN.JSON_PARSING_FAILED.format(errorMessage));
                securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
                throw new TokenValidationException(
                        EventType.JWKS_JSON_PARSE_FAILED,
                        JWTValidationLogMessages.WARN.JSON_PARSING_FAILED.format(errorMessage)
                );
            }

            // Regular parsing errors
            LOGGER.error(e, JWTValidationLogMessages.ERROR.JSON_PARSE_FAILED.format(wellKnownUrl, e.getMessage()));
            return Optional.empty();
        }
    }

    /**
     * Legacy method for backward compatibility.
     * 
     * @deprecated Access fields directly from {@link WellKnownConfiguration}
     */
    @Deprecated
    Optional<String> getString(JsonObject jsonObject, String key) {
        // Fallback for JsonObject implementations
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
     * Determines if an IOException is caused by a DSL-JSON security limit violation.
     */
    private boolean isSecurityLimitViolation(@Nullable String errorMessage) {
        if (errorMessage == null) {
            return false;
        }
        return errorMessage.contains("buffer") ||
                errorMessage.contains("limit") ||
                errorMessage.contains("too large") ||
                errorMessage.contains("exceeded");
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