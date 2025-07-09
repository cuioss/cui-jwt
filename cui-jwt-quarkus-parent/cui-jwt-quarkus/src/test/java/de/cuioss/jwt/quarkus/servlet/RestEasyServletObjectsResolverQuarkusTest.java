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

import de.cuioss.jwt.quarkus.annotation.ServletObjectsResolver;
import de.cuioss.jwt.quarkus.config.JwtTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Quarkus integration tests for {@link RestEasyServletObjectsResolver}.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@QuarkusTest
@TestProfile(JwtTestProfile.class)
@DisplayName("RestEasyServletObjectsResolver Quarkus Integration Tests")
class RestEasyServletObjectsResolverQuarkusTest {

    @Inject
    @ServletObjectsResolver(ServletObjectsResolver.Variant.RESTEASY)
    HttpServletRequestResolver resolver;

    @Test
    @DisplayName("Should return empty Optional when no RESTEasy context in Quarkus")
    void shouldReturnEmptyOptionalWhenNoResteasyContextInQuarkus() {
        // Even in Quarkus context, without an active REST request, should return empty Optional
        Optional<HttpServletRequest> httpServletRequest = resolver.resolveHttpServletRequest();

        assertTrue(httpServletRequest.isEmpty(), "HttpServletRequest should be empty outside REST request context");
    }

    @Test
    @DisplayName("Should return empty Optional for header map when no RESTEasy context in Quarkus")
    void shouldReturnEmptyOptionalForHeaderMapWhenNoResteasyContextInQuarkus() {
        // Even in Quarkus context, without an active REST request, should return empty Optional
        Optional<Map<String, List<String>>> headerMap = resolver.resolveHeaderMap();

        assertTrue(headerMap.isEmpty(), "HeaderMap should be empty outside REST request context");
    }
}