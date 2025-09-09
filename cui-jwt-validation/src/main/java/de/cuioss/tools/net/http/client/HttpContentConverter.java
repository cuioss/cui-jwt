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

import lombok.NonNull;

import java.util.Optional;

/**
 * Content converter for transforming raw HTTP response strings into typed objects.
 * <p>
 * This interface allows ETagAwareHttpHandler to be generic over different content types
 * while maintaining HTTP response body handling as String internally.
 * <p>
 * Implementations should handle conversion errors gracefully by returning Optional.empty()
 * when conversion fails or when there is no meaningful content to convert.
 *
 * @param <T> the target type for content conversion
 * @author Oliver Wolff
 * @since 1.0
 */
public interface HttpContentConverter<T> {

    /**
     * Converts raw HTTP response body to the target type.
     * <p>
     * Returns Optional.empty() when:
     * <ul>
     *   <li>Conversion fails due to malformed content</li>
     *   <li>Content is empty or null</li>
     *   <li>Content cannot be meaningfully converted</li>
     * </ul>
     *
     * @param rawContent the raw HTTP response body as String (may be null or empty)
     * @return Optional containing converted content, or empty if conversion fails
     */
    Optional<T> convert(String rawContent);

    /**
     * Provides a semantically correct empty value for this content type.
     * <p>
     * This method should return a meaningful "empty" representation that makes sense
     * for the target type T, rather than trying to convert meaningless input.
     * <p>
     * Examples:
     * <ul>
     *   <li>For JSON: empty JsonNode or empty object</li>
     *   <li>For String: empty string</li>
     *   <li>For Collections: empty collection</li>
     *   <li>For custom objects: default/empty instance</li>
     * </ul>
     *
     * @return semantically correct empty value for type T, never null
     */
    @NonNull
    T emptyValue();

    /**
     * Identity converter for String content (no conversion needed).
     *
     * @return converter that returns the input unchanged
     */
    static HttpContentConverter<String> identity() {
        return new HttpContentConverter<>() {
            @Override
            public Optional<String> convert(String rawContent) {
                return Optional.ofNullable(rawContent);
            }

            @Override
            @NonNull
            public String emptyValue() {
                return "";
            }
        };
    }
}