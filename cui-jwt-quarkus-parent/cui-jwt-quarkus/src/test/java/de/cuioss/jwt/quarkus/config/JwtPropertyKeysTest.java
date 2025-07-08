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
package de.cuioss.jwt.quarkus.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("JwtPropertyKeys")
class JwtPropertyKeysTest {

    @Nested
    @DisplayName("Base Properties")
    class BaseProperties {

        @Test
        @DisplayName("should have correct prefix")
        void shouldHaveCorrectPrefix() {
            assertEquals("cui.jwt", JwtPropertyKeys.PREFIX);
        }

        @Test
        @DisplayName("should have correct dot jwks suffix")
        void shouldHaveCorrectDotJwksSuffix() {
            assertEquals(".jwks", JwtPropertyKeys.DOT_JWKS);
        }
    }

    @Nested
    @DisplayName("Parser Properties")
    class ParserProperties {

        @Test
        @DisplayName("should have correct base path")
        void shouldHaveCorrectBasePath() {
            assertEquals("cui.jwt.parser", JwtPropertyKeys.PARSER.BASE);
        }

        @Test
        @DisplayName("should have parser properties with correct prefix")
        void shouldHaveParserPropertiesWithCorrectPrefix() {
            assertTrue(JwtPropertyKeys.PARSER.MAX_TOKEN_SIZE.startsWith(JwtPropertyKeys.PARSER.BASE));
            assertTrue(JwtPropertyKeys.PARSER.MAX_PAYLOAD_SIZE.startsWith(JwtPropertyKeys.PARSER.BASE));
            assertTrue(JwtPropertyKeys.PARSER.MAX_STRING_SIZE.startsWith(JwtPropertyKeys.PARSER.BASE));
            assertTrue(JwtPropertyKeys.PARSER.MAX_ARRAY_SIZE.startsWith(JwtPropertyKeys.PARSER.BASE));
            assertTrue(JwtPropertyKeys.PARSER.MAX_DEPTH.startsWith(JwtPropertyKeys.PARSER.BASE));
        }
    }

    @Nested
    @DisplayName("Issuer Properties")
    class IssuerProperties {

        @Test
        @DisplayName("should format issuer base property correctly")
        void shouldFormatIssuerBasePropertyCorrectly() {
            var issuerName = "default";
            var expected = "cui.jwt.issuers.default.";
            var actual = JwtPropertyKeys.ISSUERS.BASE.formatted(issuerName);

            assertEquals(expected, actual);
        }

        @Test
        @DisplayName("should format issuer enabled property correctly")
        void shouldFormatIssuerEnabledPropertyCorrectly() {
            var issuerName = "keycloak";
            var expected = "cui.jwt.issuers.keycloak.enabled";
            var actual = JwtPropertyKeys.ISSUERS.ENABLED.formatted(issuerName);

            assertEquals(expected, actual);
        }

        @Test
        @DisplayName("should format JWKS URL property correctly")
        void shouldFormatJwksUrlPropertyCorrectly() {
            var issuerName = "auth0";
            var expected = "cui.jwt.issuers.auth0.jwks.http.url";
            var actual = JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(issuerName);

            assertEquals(expected, actual);
        }

        @Test
        @DisplayName("should format well-known URL property correctly")
        void shouldFormatWellKnownUrlPropertyCorrectly() {
            var issuerName = "oidc";
            var expected = "cui.jwt.issuers.oidc.jwks.http.well-known-url";
            var actual = JwtPropertyKeys.ISSUERS.WELL_KNOWN_URL.formatted(issuerName);

            assertEquals(expected, actual);
        }
    }

    @Nested
    @DisplayName("Health Properties")
    class HealthProperties {

        @Test
        @DisplayName("should have correct health base path")
        void shouldHaveCorrectHealthBasePath() {
            assertEquals("cui.jwt.health", JwtPropertyKeys.HEALTH.BASE);
        }

        @Test
        @DisplayName("should have correct JWKS health properties")
        void shouldHaveCorrectJwksHealthProperties() {
            assertEquals("cui.jwt.health.jwks", JwtPropertyKeys.HEALTH.JWKS.BASE);
            assertTrue(JwtPropertyKeys.HEALTH.JWKS.CACHE_SECONDS.startsWith(JwtPropertyKeys.HEALTH.JWKS.BASE));
            assertTrue(JwtPropertyKeys.HEALTH.JWKS.TIMEOUT_SECONDS.startsWith(JwtPropertyKeys.HEALTH.JWKS.BASE));
        }
    }

    @Nested
    @DisplayName("Metrics Properties")
    class MetricsProperties {

        @Test
        @DisplayName("should have correct metrics base paths")
        void shouldHaveCorrectMetricsBasePaths() {
            assertEquals("cui.jwt.validation", JwtPropertyKeys.METRICS.BASE);
            assertEquals("cui.jwt.jwks", JwtPropertyKeys.METRICS.JWKS_BASE);
        }

        @Test
        @DisplayName("should have metrics properties with correct prefixes")
        void shouldHaveMetricsPropertiesWithCorrectPrefixes() {
            assertTrue(JwtPropertyKeys.METRICS.VALIDATION_ERRORS.startsWith(JwtPropertyKeys.METRICS.BASE));
            assertTrue(JwtPropertyKeys.METRICS.JWKS_CACHE_SIZE.startsWith(JwtPropertyKeys.METRICS.JWKS_BASE));
        }
    }
}