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
package de.cuioss.jwt.validation.well_known;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.tools.net.http.HttpHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for {@link WellKnownEndpointMapper}.
 *
 * @author Oliver Wolff
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("WellKnownEndpointMapper")
class WellKnownEndpointMapperTest {

    private static final String ENDPOINT_KEY = "jwks_uri";
    private static final String VALID_URL = "https://example.com/.well-known/jwks.json";

    private WellKnownEndpointMapper mapper;
    private URL wellKnownUrl;
    private Map<String, HttpHandler> endpointMap;

    @BeforeEach
    void setup() throws MalformedURLException {
        wellKnownUrl = URI.create("https://example.com/.well-known/openid-configuration").toURL();
        endpointMap = new HashMap<>();
        HttpHandler baseHandler = HttpHandler.builder().url(wellKnownUrl.toString()).build();
        mapper = new WellKnownEndpointMapper(baseHandler);
    }

    @Test
    @DisplayName("Should add HttpHandler to map for valid URL")
    void shouldAddHttpHandlerToMapForValidUrl() {
        boolean result = mapper.addHttpHandlerToMap(endpointMap, ENDPOINT_KEY, VALID_URL, wellKnownUrl, true);

        assertTrue(result);
        assertTrue(endpointMap.containsKey(ENDPOINT_KEY));
        assertNotNull(endpointMap.get(ENDPOINT_KEY));
        assertEquals(VALID_URL, endpointMap.get(ENDPOINT_KEY).getUrl().toString());
    }

    @ParameterizedTest(name = "URL: {0}, Required: {1}, Should fail: {2}")
    @CsvSource({
            "'<null>', false, false",
            "'<null>', true, true",
            "'http://example.com/invalid path with spaces', true, true",
            "'http://example.com/invalid path with spaces', false, true",
            "'', true, true",
            "'', false, true"
    })
    @DisplayName("Should handle various URL scenarios")
    void shouldHandleVariousUrlScenarios(String urlInput, boolean required, boolean shouldFail) {
        // Convert "<null>" string to actual null
        String url = "<null>".equals(urlInput) ? null : urlInput;

        boolean result = mapper.addHttpHandlerToMap(endpointMap, ENDPOINT_KEY, url, wellKnownUrl, required);

        if (shouldFail) {
            assertFalse(result);
        } else {
            assertTrue(result);
        }

        // Check if endpoint was added based on success/failure and whether it was valid
        if (result && url != null && !url.isEmpty()) {
            assertTrue(endpointMap.containsKey(ENDPOINT_KEY));
        } else {
            assertFalse(endpointMap.containsKey(ENDPOINT_KEY));
        }
    }

    @Test
    @DisplayName("Should handle multiple endpoint URLs")
    void shouldHandleMultipleEndpointUrls() {
        String secondKey = "token_endpoint";
        String secondUrl = "https://example.com/token";

        boolean result1 = mapper.addHttpHandlerToMap(endpointMap, ENDPOINT_KEY, VALID_URL, wellKnownUrl, true);
        boolean result2 = mapper.addHttpHandlerToMap(endpointMap, secondKey, secondUrl, wellKnownUrl, true);

        assertTrue(result1);
        assertTrue(result2);
        assertEquals(2, endpointMap.size());
        assertTrue(endpointMap.containsKey(ENDPOINT_KEY));
        assertTrue(endpointMap.containsKey(secondKey));
        assertEquals(VALID_URL, endpointMap.get(ENDPOINT_KEY).getUrl().toString());
        assertEquals(secondUrl, endpointMap.get(secondKey).getUrl().toString());
    }

    @Test
    @DisplayName("Should handle null handler in accessibility check")
    void shouldHandleNullHandlerInAccessibilityCheck() {
        assertDoesNotThrow(() -> mapper.performAccessibilityCheck("jwks_uri", null));
    }

    @Test
    @DisplayName("Should override existing endpoint in map")
    void shouldOverrideExistingEndpointInMap() {
        String newUrl = "https://example.com/new-jwks.json";

        // Add first endpoint
        mapper.addHttpHandlerToMap(endpointMap, ENDPOINT_KEY, VALID_URL, wellKnownUrl, true);
        assertEquals(1, endpointMap.size());

        // Override with new URL
        mapper.addHttpHandlerToMap(endpointMap, ENDPOINT_KEY, newUrl, wellKnownUrl, true);
        assertEquals(1, endpointMap.size());
        assertTrue(endpointMap.containsKey(ENDPOINT_KEY));
        assertEquals(newUrl, endpointMap.get(ENDPOINT_KEY).getUrl().toString());
    }

}
