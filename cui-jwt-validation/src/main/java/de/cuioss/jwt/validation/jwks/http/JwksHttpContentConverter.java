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
package de.cuioss.jwt.validation.jwks.http;

import com.dslplatform.json.DslJson;
import de.cuioss.http.client.converter.StringContentConverter;
import de.cuioss.jwt.validation.ParserConfig;
import de.cuioss.jwt.validation.json.Jwks;
import de.cuioss.tools.logging.CuiLogger;
import lombok.NonNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * HTTP content converter for JSON Web Key Set (JWKS) content.
 * <p>
 * This converter handles the transformation of HTTP String responses
 * containing JWKS JSON data into Jwks objects using DSL-JSON for
 * high-performance parsing.
 * <p>
 * The converter is thread-safe and reusable across multiple HTTP requests.
 * It follows the CUI converter pattern by extending StringContentConverter
 * and provides proper empty value semantics for error cases.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public class JwksHttpContentConverter extends StringContentConverter<Jwks> {

    private static final CuiLogger LOGGER = new CuiLogger(JwksHttpContentConverter.class);

    private final DslJson<Object> dslJson;

    /**
     * Creates a new JWKS content converter with default parser configuration.
     */
    public JwksHttpContentConverter() {
        this(ParserConfig.builder().build());
    }

    /**
     * Creates a new JWKS content converter with specified parser configuration.
     *
     * @param parserConfig the parser configuration to use for DSL-JSON
     */
    public JwksHttpContentConverter(@NonNull ParserConfig parserConfig) {
        super(StandardCharsets.UTF_8);
        this.dslJson = parserConfig.getDslJson();
    }

    @Override
    protected Optional<Jwks> convertString(String rawContent) {
        if (rawContent == null || rawContent.trim().isEmpty()) {
            LOGGER.debug("Empty or null JWKS content received");
            return Optional.of(emptyValue());
        }

        try {
            byte[] bodyBytes = rawContent.getBytes(StandardCharsets.UTF_8);
            Jwks jwks = dslJson.deserialize(Jwks.class, bodyBytes, bodyBytes.length);

            if (jwks == null) {
                LOGGER.warn("DSL-JSON returned null for JWKS parsing");
                return Optional.empty();
            }

            LOGGER.debug("Successfully parsed JWKS with %s keys",
                    jwks.keys() != null ? jwks.keys().size() : 0);
            return Optional.of(jwks);

        } catch (IOException e) {
            LOGGER.warn("Failed to parse JWKS content: %s", e.getMessage());
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Invalid JWKS JSON structure: %s", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    @NonNull
    public Jwks emptyValue() {
        return Jwks.empty();
    }
}