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
import de.cuioss.jwt.validation.json.Jwks;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * HTTP content converter for JWKS using DSL-JSON mapper approach.
 * <p>
 * This converter uses DSL-JSON's compile-time code generation to convert
 * HTTP responses directly to typed Jwks objects, providing high-performance
 * parsing without reflection.
 * 
 * @author Generated
 * @since 1.0
 */
public class JwksConverter implements HttpContentConverter<Jwks> {

    private final DslJson<Object> dslJson;

    public JwksConverter(@NonNull DslJson<Object> dslJson) {
        this.dslJson = dslJson;
    }

    @Override
    public Optional<Jwks> convert(Object rawContent) {
        try {
            String body = (rawContent instanceof String s) ? s :
                    (rawContent != null) ? rawContent.toString() : null;
            if (body == null || body.trim().isEmpty()) {
                return Optional.of(emptyValue());
            }

            // Parse using DSL-JSON directly to Jwks object
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            Jwks jwks = dslJson.deserialize(Jwks.class, bytes, bytes.length);

            return Optional.ofNullable(jwks);
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    @Override
    public HttpResponse.BodyHandler<?> getBodyHandler() {
        return HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
    }

    @Override
    @NonNull
    public Jwks emptyValue() {
        return Jwks.empty();
    }
}