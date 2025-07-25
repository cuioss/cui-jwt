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
package de.cuioss.jwt.validation.jwks.key;

import de.cuioss.jwt.validation.JWTValidationLogMessages.WARN;
import de.cuioss.jwt.validation.ParserConfig;
import de.cuioss.jwt.validation.jwks.JwksLoader;
import de.cuioss.jwt.validation.jwks.JwksType;
import de.cuioss.jwt.validation.jwks.LoaderStatus;
import de.cuioss.jwt.validation.jwks.parser.JwksParser;
import de.cuioss.jwt.validation.jwks.parser.KeyProcessor;
import de.cuioss.jwt.validation.security.JwkAlgorithmPreferences;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.jwt.validation.security.SecurityEventCounter.EventType;
import de.cuioss.tools.logging.CuiLogger;
import de.cuioss.tools.string.MoreStrings;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of {@link JwksLoader} that loads JWKS from string content.
 * <p>
 * This implementation processes JWKS content that is provided as a string. 
 * If file-based JWKS loading is needed, the file content is resolved to string 
 * at build time through the builder pattern.
 * <p>
 * This implementation supports cryptographic agility by handling multiple key types
 * and algorithms, including RSA, EC, and RSA-PSS.
 * <p>
 * This class processes and stores the parsed JWKS keys in memory for efficient access.
 * <p>
 * Security features:
 * <ul>
 *   <li>JWKS content size validation to prevent memory exhaustion attacks</li>
 *   <li>Secure JSON parsing with limits on string size, array size, and depth</li>
 *   <li>Security event tracking for monitoring and alerting</li>
 * </ul>
 * <p>
 * For more details on the security aspects, see the
 * <a href="https://github.com/cuioss/cui-jwt/tree/main/doc/specification/security.adoc">Security Specification</a>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@ToString(of = {"keyInfoMap", "parserConfig", "securityEventCounter"})
@EqualsAndHashCode(of = {"keyInfoMap", "parserConfig", "securityEventCounter"})
public class JWKSKeyLoader implements JwksLoader {

    private static final CuiLogger LOGGER = new CuiLogger(JWKSKeyLoader.class);

    private final ParserConfig parserConfig;
    private SecurityEventCounter securityEventCounter;
    @NonNull
    private final JwkAlgorithmPreferences jwkAlgorithmPreferences;
    @NonNull
    private final JwksType jwksType;
    private volatile LoaderStatus status;
    private Map<String, KeyInfo> keyInfoMap;

    // Field for deferred initialization
    private final String jwksContent;
    private volatile boolean initialized = false;

    /**
     * Builder for JWKSKeyLoader.
     */
    public static class JWKSKeyLoaderBuilder {
        private String jwksContent;
        private String jwksFilePath;
        private ParserConfig parserConfig = ParserConfig.builder().build();
        private JwkAlgorithmPreferences jwkAlgorithmPreferences = new JwkAlgorithmPreferences(); // Default instance
        private JwksType jwksType = JwksType.MEMORY; // Default to MEMORY type

        JWKSKeyLoaderBuilder() {
        }

        /**
         * Sets the JWKS content string to be parsed.
         *
         * @param jwksContent the JWKS content as a string
         * @return this builder
         */
        public JWKSKeyLoaderBuilder jwksContent(String jwksContent) {
            this.jwksContent = jwksContent;
            return this;
        }

        /**
         * Sets the parser configuration.
         *
         * @param parserConfig the parser configuration
         * @return this builder
         */
        public JWKSKeyLoaderBuilder parserConfig(ParserConfig parserConfig) {
            this.parserConfig = parserConfig != null ? parserConfig : ParserConfig.builder().build();
            return this;
        }

        /**
         * Sets the JWKS file path for deferred loading.
         *
         * @param jwksFilePath the path to the JWKS file
         * @return this builder
         */
        public JWKSKeyLoaderBuilder jwksFilePath(String jwksFilePath) {
            this.jwksFilePath = jwksFilePath;
            return this;
        }

        /**
         * Sets the JWK algorithm preferences.
         *
         * @param jwkAlgorithmPreferences the JWK algorithm preferences
         * @return this builder
         */
        public JWKSKeyLoaderBuilder jwkAlgorithmPreferences(JwkAlgorithmPreferences jwkAlgorithmPreferences) {
            this.jwkAlgorithmPreferences = jwkAlgorithmPreferences != null ? jwkAlgorithmPreferences : new JwkAlgorithmPreferences();
            return this;
        }

        /**
         * Sets the JWKS source type.
         *
         * @param jwksType the JWKS source type
         * @return this builder
         */
        public JWKSKeyLoaderBuilder jwksType(JwksType jwksType) {
            this.jwksType = jwksType;
            return this;
        }

        /**
         * Builds a new JWKSKeyLoader with deferred initialization.
         * If jwksFilePath is provided, it will be resolved to content at build time.
         * The SecurityEventCounter must be set via initJWKSLoader() before use.
         *
         * @return a new JWKSKeyLoader
         * @throws IllegalArgumentException if neither jwksContent nor jwksFilePath is provided or if jwksFilePath cannot be read
         */
        @NonNull
        public JWKSKeyLoader build() {
            if (jwksContent == null && jwksFilePath == null) {
                throw new IllegalArgumentException("Either jwksContent or jwksFilePath must be provided");
            }

            if (jwksContent != null) {
                // Direct content provided
                return new JWKSKeyLoader(jwksContent, parserConfig, jwkAlgorithmPreferences, jwksType);
            } else {
                // File path provided - resolve at build time and fail fast if unable to read
                String resolvedContent = loadJwksFromFile(jwksFilePath);
                return new JWKSKeyLoader(resolvedContent, parserConfig, jwkAlgorithmPreferences, jwksType);
            }
        }

        /**
         * Loads JWKS content from a file path.
         * Fails fast if file cannot be read.
         *
         * @param filePath the path to the JWKS file
         * @return the JWKS content as string
         * @throws IllegalArgumentException if file cannot be read
         */
        private String loadJwksFromFile(String filePath) {
            try {
                String content = new String(Files.readAllBytes(Path.of(filePath)));
                LOGGER.debug("Successfully read JWKS from file: %s", filePath);
                return content;
            } catch (IOException e) {
                throw new IllegalArgumentException("Cannot read JWKS file: " + filePath, e);
            }
        }
    }

    /**
     * Creates a new builder for JWKSKeyLoader.
     *
     * @return a new builder
     */
    @NonNull
    public static JWKSKeyLoaderBuilder builder() {
        return new JWKSKeyLoaderBuilder();
    }


    /**
     * Creates a new JWKSKeyLoader with deferred initialization.
     * The SecurityEventCounter must be set via initJWKSLoader() before use.
     *
     * @param jwksContent the JWKS content as a string, must not be null
     * @param parserConfig the configuration for parsing, may be null (defaults to a new instance)
     * @param jwkAlgorithmPreferences the JWK algorithm preferences for parsing validation, must not be null
     * @param jwksType the type of JWKS source, must not be null
     */
    public JWKSKeyLoader(
            @NonNull String jwksContent,
            ParserConfig parserConfig,
            @NonNull JwkAlgorithmPreferences jwkAlgorithmPreferences,
            @NonNull JwksType jwksType) {
        this.jwksContent = jwksContent;
        this.parserConfig = parserConfig != null ? parserConfig : ParserConfig.builder().build();
        this.jwkAlgorithmPreferences = jwkAlgorithmPreferences;
        this.jwksType = jwksType;
        this.status = LoaderStatus.UNDEFINED;
    }


    /**
     * Checks if this loader contains valid keys.
     *
     * @return true if the loader contains at least one valid key, false otherwise
     */
    public boolean isNotEmpty() {
        ensureInitialized();
        return keyInfoMap != null && !keyInfoMap.isEmpty();
    }

    private void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("JWKSKeyLoader not initialized. Call initJWKSLoader() first.");
        }
    }


    @Override
    public Optional<KeyInfo> getKeyInfo(String kid) {
        ensureInitialized();
        if (MoreStrings.isBlank(kid)) {
            LOGGER.debug("Key ID is null or empty");
            return Optional.empty();
        }

        return Optional.ofNullable(keyInfoMap.get(kid));
    }


    /**
     * Gets the type of JWKS source used by this loader.
     *
     * @return the JWKS source type
     */
    @Override
    public @NonNull JwksType getJwksType() {
        return jwksType;
    }

    /**
     * Checks the JWKS loader health and returns detailed status information.
     * <p>
     * For in-memory and file-based loaders, this checks if keys were successfully parsed
     * during construction and are currently available.
     *
     * @return the current health status, considering both loader status and key availability
     */
    @Override
    public @NonNull LoaderStatus isHealthy() {
        if (!initialized || keyInfoMap == null) {
            return LoaderStatus.UNDEFINED;
        }
        if (status == LoaderStatus.OK && !keyInfoMap.isEmpty()) {
            return LoaderStatus.OK;
        } else if (status == LoaderStatus.ERROR) {
            return LoaderStatus.ERROR;
        } else {
            return LoaderStatus.UNDEFINED;
        }
    }

    @Override
    public Optional<String> getIssuerIdentifier() {
        // In-memory and file-based loaders don't have associated issuer identifiers
        return Optional.empty();
    }

    @Override
    public void initJWKSLoader(@NonNull SecurityEventCounter securityEventCounter) {
        if (!initialized) {
            this.securityEventCounter = securityEventCounter;
            this.initialized = true;
            initializeKeys();
            LOGGER.debug("JWKSKeyLoader initialized with SecurityEventCounter");
        }
    }

    /**
     * Initializes the JWKS keys by parsing the content.
     */
    private void initializeKeys() {
        try {
            parseAndProcessKeys(jwksContent);
        } catch (JsonException | IllegalArgumentException e) {
            handleParseError(e);
        }
    }

    /**
     * Parses and processes JWKS content into KeyInfo objects.
     *
     * @param contentToProcess the JWKS content to parse
     */
    private void parseAndProcessKeys(String contentToProcess) {
        // Parse JWKS content
        JwksParser parser = new JwksParser(this.parserConfig, this.securityEventCounter);
        KeyProcessor processor = new KeyProcessor(this.securityEventCounter, this.jwkAlgorithmPreferences);

        // Parse JWKS content to get individual JWK objects (includes structure validation)
        List<JsonObject> jwkObjects = parser.parse(contentToProcess);

        // Process each key (includes key parameter validation)
        Map<String, KeyInfo> keyMap = new ConcurrentHashMap<>();
        for (JsonObject jwk : jwkObjects) {
            var keyInfoOpt = processor.processKey(jwk);
            keyInfoOpt.ifPresent(keyInfo -> keyMap.put(keyInfo.keyId(), keyInfo));
        }

        this.keyInfoMap = keyMap;
        this.status = keyMap.isEmpty() ? LoaderStatus.ERROR : LoaderStatus.OK;
        LOGGER.debug("Successfully loaded %s key(s)", keyMap.size());
    }

    /**
     * Handles parse errors by logging and setting error state.
     *
     * @param e the exception that occurred during parsing
     */
    private void handleParseError(Exception e) {
        LOGGER.warn(e, WARN.JWKS_JSON_PARSE_FAILED.format(e.getMessage()));
        securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
        this.keyInfoMap = new ConcurrentHashMap<>();
        this.status = LoaderStatus.ERROR;
    }
}
