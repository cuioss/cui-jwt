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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tests WellKnownConfig")
class WellKnownConfigTest {

    private static final String TEST_WELL_KNOWN_URL = "https://example.com/.well-known/openid-configuration";
    private static final URI TEST_WELL_KNOWN_URI = URI.create(TEST_WELL_KNOWN_URL);

    @Test
    @DisplayName("Should create config with URL string")
    void shouldCreateConfigWithUrl() {
        WellKnownConfig config = WellKnownConfig.builder()
                .wellKnownUrl(TEST_WELL_KNOWN_URL)
                .build();

        assertNotNull(config.getHttpHandler());
        assertEquals(TEST_WELL_KNOWN_URI.toString(), config.getHttpHandler().getUri().toString());
    }

    @Test
    @DisplayName("Should create config with URI")
    void shouldCreateConfigWithUri() {
        WellKnownConfig config = WellKnownConfig.builder()
                .wellKnownUri(TEST_WELL_KNOWN_URI)
                .build();

        assertNotNull(config.getHttpHandler());
        assertEquals(TEST_WELL_KNOWN_URI, config.getHttpHandler().getUri());
    }

    @Test
    @DisplayName("Should create config with custom timeouts")
    void shouldCreateConfigWithCustomTimeouts() {
        WellKnownConfig config = WellKnownConfig.builder()
                .wellKnownUrl(TEST_WELL_KNOWN_URL)
                .connectTimeoutSeconds(5)
                .readTimeoutSeconds(10)
                .build();

        assertNotNull(config.getHttpHandler());
        // HTTP handler should be created with the custom timeouts (can't directly verify timeouts from HttpHandler)
    }

    @Test
    @DisplayName("Should fail when no well-known URI configured")
    void shouldFailWhenNoWellKnownUri() {
        WellKnownConfig.WellKnownConfigBuilder builder = WellKnownConfig.builder();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, builder::build);
        assertTrue(exception.getMessage().contains("Invalid well-known endpoint configuration"));
    }

    @Test
    @DisplayName("Should fail with invalid timeout values")
    void shouldFailWithInvalidTimeouts() {
        // Test invalid connect timeout
        var builderWithInvalidConnectTimeout = WellKnownConfig.builder()
                .wellKnownUrl(TEST_WELL_KNOWN_URL)
                .connectTimeoutSeconds(0);

        assertThrows(IllegalArgumentException.class, builderWithInvalidConnectTimeout::build);

        // Test invalid read timeout
        var builderWithInvalidReadTimeout = WellKnownConfig.builder()
                .wellKnownUrl(TEST_WELL_KNOWN_URL)
                .readTimeoutSeconds(-1);

        assertThrows(IllegalArgumentException.class, builderWithInvalidReadTimeout::build);
    }
}