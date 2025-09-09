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
package de.cuioss.tools.net.http.client;

import de.cuioss.jwt.validation.JWTValidationLogMessages;
import de.cuioss.jwt.validation.ParserConfig;
import de.cuioss.tools.logging.CuiLogger;
import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import lombok.NonNull;

import java.io.StringReader;
import java.util.Optional;

/**
 * JSON content converter that uses ParserConfig for secure JSON parsing.
 * <p>
 * This converter processes String-based HTTP responses as JSON using the security
 * settings from ParserConfig, including limits on string size, array size, and nesting depth.
 * <p>
 * The converter leverages ParserConfig's JsonReaderFactory to ensure consistent
 * security settings across all JSON parsing operations in the JWT validation system.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public class JsonContentConverter extends StringContentConverter<JsonObject> {

    private static final CuiLogger LOGGER = new CuiLogger(JsonContentConverter.class);

    private final ParserConfig parserConfig;

    /**
     * Creates a JSON content converter with the specified parser configuration.
     *
     * @param parserConfig the parser configuration containing JSON security settings
     */
    public JsonContentConverter(@NonNull ParserConfig parserConfig) {
        this.parserConfig = parserConfig;
    }

    @Override
    protected Optional<JsonObject> convertString(String rawContent) {
        if (rawContent == null || rawContent.trim().isEmpty()) {
            return Optional.empty();
        }

        try {
            try (JsonReader jsonReader = parserConfig.getJsonReaderFactory().createReader(new StringReader(rawContent))) {
                JsonObject jsonObject = jsonReader.readObject();
                return Optional.of(jsonObject);
            }
        } catch (JsonException | IllegalStateException e) {
            // JSON parsing failed - could be malformed JSON, I/O error, security limits exceeded, or reader state error
            LOGGER.warn(e, JWTValidationLogMessages.WARN.JSON_PARSING_FAILED.format(e.getMessage()));
            return Optional.empty();
        }
    }

    @Override
    @NonNull
    public JsonObject emptyValue() {
        return Json.createObjectBuilder().build();
    }
}