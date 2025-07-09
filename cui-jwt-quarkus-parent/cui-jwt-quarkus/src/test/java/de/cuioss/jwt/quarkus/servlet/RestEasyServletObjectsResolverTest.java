/**
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
package de.cuioss.jwt.quarkus.servlet;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link RestEasyServletObjectsResolver}.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@DisplayName("RestEasyServletObjectsResolver Tests")
class RestEasyServletObjectsResolverTest {

    @Test
    @DisplayName("Should return empty Optional when no RESTEasy context available")
    void shouldReturnEmptyOptionalWhenNoResteasyContext() {
        // Outside of RESTEasy context, should return empty Optional
        RestEasyServletObjectsResolver resolver = new RestEasyServletObjectsResolver();
        Optional<HttpServletRequest> httpServletRequest = resolver.resolveHttpServletRequest();

        assertTrue(httpServletRequest.isEmpty(), "HttpServletRequest should be empty outside RESTEasy context");
    }

    @Test
    @DisplayName("Should return empty Optional for header map when no RESTEasy context available")
    void shouldReturnEmptyOptionalForHeaderMapWhenNoResteasyContext() {
        // Outside of RESTEasy context, should return empty Optional
        RestEasyServletObjectsResolver resolver = new RestEasyServletObjectsResolver();
        Optional<Map<String, List<String>>> headerMap = resolver.resolveHeaderMap();

        assertTrue(headerMap.isEmpty(), "HeaderMap should be empty outside RESTEasy context");
    }

    @Test
    @DisplayName("Should handle ResteasyProviderFactory being null")
    void shouldHandleResteasyProviderFactoryBeingNull() {
        // This test verifies that the resolver handles the case where ResteasyProviderFactory.getInstance() returns null
        // In a unit test environment, this is the expected behavior

        RestEasyServletObjectsResolver resolver = new RestEasyServletObjectsResolver();
        Optional<HttpServletRequest> httpServletRequest = resolver.resolveHttpServletRequest();
        Optional<Map<String, List<String>>> headerMap = resolver.resolveHeaderMap();

        assertTrue(httpServletRequest.isEmpty(), "HttpServletRequest should be empty when ResteasyProviderFactory is null");
        assertTrue(headerMap.isEmpty(), "HeaderMap should be empty when ResteasyProviderFactory is null");
    }

}