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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HttpServletRequestResolver} interface.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@DisplayName("HttpServletRequestResolver Interface")
class HttpServletRequestResolverTest {

    @Nested
    @DisplayName("Default Implementation Behavior")
    class DefaultImplementationBehavior {

        @Test
        @DisplayName("should return empty header map when no request available")
        void shouldReturnEmptyHeaderMapWhenNoRequestAvailable() {
            HttpServletRequestResolver resolver = new HttpServletRequestResolver() {
                @Override
                public Optional<HttpServletRequest> resolveHttpServletRequest() {
                    return Optional.empty();
                }
            };

            Optional<Map<String, List<String>>> result = resolver.resolveHeaderMap();

            assertTrue(result.isEmpty(), "resolveHeaderMap should return empty when no request available");
        }

        @Test
        @DisplayName("should extract headers from available request")
        void shouldExtractHeadersFromAvailableRequest() {
            HttpServletRequestMock mockRequest = new HttpServletRequestMock();
            mockRequest.setHeader("Authorization", "Bearer token123");
            mockRequest.addHeader("X-Custom-Header", "value1");
            mockRequest.addHeader("X-Custom-Header", "value2");
            mockRequest.setHeader("Content-Type", "application/json");

            HttpServletRequestResolver resolver = new HttpServletRequestResolver() {
                @Override
                public Optional<HttpServletRequest> resolveHttpServletRequest() {
                    return Optional.of(mockRequest);
                }
            };

            Optional<Map<String, List<String>>> result = resolver.resolveHeaderMap();

            assertTrue(result.isPresent(), "Should return header map when request is available");
            Map<String, List<String>> headers = result.get();

            assertEquals(3, headers.size(), "Should have 3 different headers");

            assertEquals(List.of("Bearer token123"), headers.get("Authorization"));
            assertEquals(List.of("application/json"), headers.get("Content-Type"));
            assertEquals(List.of("value1", "value2"), headers.get("X-Custom-Header"));
        }

        @Test
        @DisplayName("should handle request with no headers")
        void shouldHandleRequestWithNoHeaders() {
            HttpServletRequestMock mockRequest = new HttpServletRequestMock();
            mockRequest.clearHeaders();

            HttpServletRequestResolver resolver = new HttpServletRequestResolver() {
                @Override
                public Optional<HttpServletRequest> resolveHttpServletRequest() {
                    return Optional.of(mockRequest);
                }
            };

            Optional<Map<String, List<String>>> result = resolver.resolveHeaderMap();

            assertTrue(result.isPresent(), "Should return header map even when no headers");
            Map<String, List<String>> headers = result.get();
            assertTrue(headers.isEmpty(), "Header map should be empty when no headers present");
        }

        @Test
        @DisplayName("should handle single-value headers")
        void shouldHandleSingleValueHeaders() {
            HttpServletRequestMock mockRequest = new HttpServletRequestMock();
            mockRequest.setHeader("Host", "localhost:8080");
            mockRequest.setHeader("User-Agent", "Test-Agent/1.0");

            HttpServletRequestResolver resolver = new HttpServletRequestResolver() {
                @Override
                public Optional<HttpServletRequest> resolveHttpServletRequest() {
                    return Optional.of(mockRequest);
                }
            };

            Optional<Map<String, List<String>>> result = resolver.resolveHeaderMap();

            assertTrue(result.isPresent());
            Map<String, List<String>> headers = result.get();

            assertEquals(2, headers.size());
            assertEquals(List.of("localhost:8080"), headers.get("Host"));
            assertEquals(List.of("Test-Agent/1.0"), headers.get("User-Agent"));
        }

        @Test
        @DisplayName("should handle multi-value headers")
        void shouldHandleMultiValueHeaders() {
            HttpServletRequestMock mockRequest = new HttpServletRequestMock();
            mockRequest.addHeader("Accept", "application/json");
            mockRequest.addHeader("Accept", "application/xml");
            mockRequest.addHeader("Accept", "text/plain");

            HttpServletRequestResolver resolver = new HttpServletRequestResolver() {
                @Override
                public Optional<HttpServletRequest> resolveHttpServletRequest() {
                    return Optional.of(mockRequest);
                }
            };

            Optional<Map<String, List<String>>> result = resolver.resolveHeaderMap();

            assertTrue(result.isPresent());
            Map<String, List<String>> headers = result.get();

            assertEquals(1, headers.size());
            List<String> acceptValues = headers.get("Accept");
            assertEquals(3, acceptValues.size());
            assertEquals(List.of("application/json", "application/xml", "text/plain"), acceptValues);
        }

        @Test
        @DisplayName("should handle headers with null values")
        void shouldHandleHeadersWithNullValues() {
            HttpServletRequestMock mockRequest = new HttpServletRequestMock();
            mockRequest.setHeader("Authorization", null);
            mockRequest.setHeader("Valid-Header", "valid-value");

            HttpServletRequestResolver resolver = new HttpServletRequestResolver() {
                @Override
                public Optional<HttpServletRequest> resolveHttpServletRequest() {
                    return Optional.of(mockRequest);
                }
            };

            Optional<Map<String, List<String>>> result = resolver.resolveHeaderMap();

            assertTrue(result.isPresent());
            Map<String, List<String>> headers = result.get();

            // The mock stores null values, so both headers are present
            // Authorization header will have a list containing null
            assertEquals(2, headers.size());
            assertEquals(List.of("valid-value"), headers.get("Valid-Header"));
            assertTrue(headers.containsKey("Authorization"));
            List<String> authValues = headers.get("Authorization");
            assertEquals(1, authValues.size());
            assertNull(authValues.getFirst());
        }
    }

    @Nested
    @DisplayName("Integration with RestEasyServletObjectsResolver")
    class RestEasyIntegration {

        @Test
        @DisplayName("should return empty outside RESTEasy context")
        void shouldReturnEmptyOutsideRestEasyContext() {
            RestEasyServletObjectsResolver resolver = new RestEasyServletObjectsResolver();

            Optional<HttpServletRequest> request = resolver.resolveHttpServletRequest();
            Optional<Map<String, List<String>>> headers = resolver.resolveHeaderMap();

            assertTrue(request.isEmpty(), "Should return empty request outside RESTEasy context");
            assertTrue(headers.isEmpty(), "Should return empty headers outside RESTEasy context");
        }
    }

    @Nested
    @DisplayName("createHeaderMapFromRequest Helper Method")
    class CreateHeaderMapFromRequestHelper {

        @Test
        @DisplayName("should create header map from request with various header types")
        void shouldCreateHeaderMapFromRequestWithVariousHeaderTypes() {
            HttpServletRequestMock mockRequest = new HttpServletRequestMock();
            mockRequest.setHeader("Authorization", "Bearer token123");
            mockRequest.setHeader("Content-Type", "application/json");
            mockRequest.addHeader("Accept", "application/json");
            mockRequest.addHeader("Accept", "application/xml");
            mockRequest.setHeader("X-Custom", "custom-value");

            HttpServletRequestResolver resolver = new HttpServletRequestResolver() {
                @Override
                public Optional<HttpServletRequest> resolveHttpServletRequest() {
                    return Optional.of(mockRequest);
                }
            };

            Map<String, List<String>> result = resolver.createHeaderMapFromRequest(mockRequest);

            assertEquals(4, result.size(), "Should have 4 different headers");
            assertEquals(List.of("Bearer token123"), result.get("Authorization"));
            assertEquals(List.of("application/json"), result.get("Content-Type"));
            assertEquals(List.of("application/json", "application/xml"), result.get("Accept"));
            assertEquals(List.of("custom-value"), result.get("X-Custom"));
        }

        @Test
        @DisplayName("should handle empty request gracefully")
        void shouldHandleEmptyRequestGracefully() {
            HttpServletRequestMock mockRequest = new HttpServletRequestMock();
            mockRequest.clearHeaders();

            HttpServletRequestResolver resolver = new HttpServletRequestResolver() {
                @Override
                public Optional<HttpServletRequest> resolveHttpServletRequest() {
                    return Optional.of(mockRequest);
                }
            };

            Map<String, List<String>> result = resolver.createHeaderMapFromRequest(mockRequest);

            assertNotNull(result, "Should return non-null map");
            assertTrue(result.isEmpty(), "Should return empty map when no headers");
        }

        @Test
        @DisplayName("should preserve header case sensitivity")
        void shouldPreserveHeaderCaseSensitivity() {
            HttpServletRequestMock mockRequest = new HttpServletRequestMock();
            mockRequest.setHeader("Content-Type", "application/json");
            mockRequest.setHeader("content-type", "text/plain");
            mockRequest.setHeader("CONTENT-TYPE", "application/xml");

            HttpServletRequestResolver resolver = new HttpServletRequestResolver() {
                @Override
                public Optional<HttpServletRequest> resolveHttpServletRequest() {
                    return Optional.of(mockRequest);
                }
            };

            Map<String, List<String>> result = resolver.createHeaderMapFromRequest(mockRequest);

            // Should maintain case sensitivity as per mock implementation
            assertEquals(3, result.size(), "Should preserve different cased headers as separate entries");
            assertTrue(result.containsKey("Content-Type"));
            assertTrue(result.containsKey("content-type"));
            assertTrue(result.containsKey("CONTENT-TYPE"));
        }
    }
}