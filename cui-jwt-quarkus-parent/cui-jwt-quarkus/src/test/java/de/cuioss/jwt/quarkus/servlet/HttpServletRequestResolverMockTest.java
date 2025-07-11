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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HttpServletRequestResolverMock}.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@DisplayName("HttpServletRequestResolverMock Tests")
class HttpServletRequestResolverMockTest {

    private HttpServletRequestResolverMock mock;

    @BeforeEach
    void setUp() {
        mock = new HttpServletRequestResolverMock();
    }


    @Test
    @DisplayName("Should return HttpServletRequest when context is available")
    void shouldReturnHttpServletRequestWhenContextAvailable() {
        // Default behavior - context available
        HttpServletRequest request = mock.resolveHttpServletRequest();

        assertNotNull(request);
        assertInstanceOf(HttpServletRequestMock.class, request);
    }

    @Test
    @DisplayName("Should throw IllegalStateException when context is not available")
    void shouldThrowIllegalStateExceptionWhenContextNotAvailable() {
        mock.setRequestContextAvailable(false);

        assertThrows(IllegalStateException.class, () -> mock.resolveHttpServletRequest());
    }

    @Test
    @DisplayName("Should return header map when context is available")
    void shouldReturnHeaderMapWhenContextAvailable() {
        mock.setHeader("Authorization", "Bearer token123");
        mock.addHeader("X-Custom", "value1");
        mock.addHeader("X-Custom", "value2");

        Map<String, List<String>> headerMap = mock.resolveHeaderMap();

        assertNotNull(headerMap);
        Map<String, List<String>> headers = headerMap;

        assertEquals("Bearer token123", headers.get("Authorization").getFirst());
        assertEquals(2, headers.get("X-Custom").size());
        assertTrue(headers.get("X-Custom").contains("value1"));
        assertTrue(headers.get("X-Custom").contains("value2"));
    }

    @Test
    @DisplayName("Should throw IllegalStateException for header map when context is not available")
    void shouldThrowIllegalStateExceptionForHeaderMapWhenContextNotAvailable() {
        mock.setHeader("Authorization", "Bearer token123");
        mock.setRequestContextAvailable(false);

        assertThrows(IllegalStateException.class, () -> mock.resolveHeaderMap());
    }

    @Test
    @DisplayName("Should support header manipulation methods")
    void shouldSupportHeaderManipulationMethods() {
        mock.setHeader("Authorization", "Bearer token123")
                .addHeader("X-Custom", "value1")
                .addHeader("X-Custom", "value2")
                .setHeader("Content-Type", "application/json");

        HttpServletRequest request = mock.resolveHttpServletRequest();

        assertEquals("Bearer token123", request.getHeader("Authorization"));
        assertEquals("application/json", request.getHeader("Content-Type"));
        assertEquals("value1", request.getHeader("X-Custom"));

        // Check multiple values
        List<String> customHeaders = Collections.list(request.getHeaders("X-Custom"));
        assertEquals(2, customHeaders.size());
        assertTrue(customHeaders.contains("value1"));
        assertTrue(customHeaders.contains("value2"));
    }

    @Test
    @DisplayName("Should support bearer token convenience method")
    void shouldSupportBearerTokenConvenienceMethod() {
        mock.setBearerToken("mytoken123");

        HttpServletRequest request = mock.resolveHttpServletRequest();

        assertEquals("Bearer mytoken123", request.getHeader("Authorization"));
    }

    @Test
    @DisplayName("Should support request property setters")
    void shouldSupportRequestPropertySetters() {
        mock.setMethod("POST")
                .setRequestURI("/api/users")
                .setContextPath("/myapp");

        HttpServletRequest request = mock.resolveHttpServletRequest();

        assertEquals("POST", request.getMethod());
        assertEquals("/api/users", request.getRequestURI());
        assertEquals("/myapp", request.getContextPath());
    }

    @Test
    @DisplayName("Should support method chaining")
    void shouldSupportMethodChaining() {
        HttpServletRequestResolverMock result = mock
                .setHeader("Authorization", "Bearer token123")
                .addHeader("X-Custom", "value1")
                .setBearerToken("newtoken")
                .setMethod("PUT")
                .setRequestURI("/api/update")
                .setRequestContextAvailable(true)
                .clearHeaders()
                .removeHeader("NonExistent");

        assertSame(mock, result);
    }

    @Test
    @DisplayName("Should provide access to underlying HttpServletRequestMock")
    void shouldProvideAccessToUnderlyingMock() {
        HttpServletRequestMock underlyingMock = mock.getHttpServletRequestMock();

        assertNotNull(underlyingMock);
        assertInstanceOf(HttpServletRequestMock.class, underlyingMock);

        // Changes to underlying mock should be reflected
        underlyingMock.setHeader("Direct", "access");

        HttpServletRequest request = mock.resolveHttpServletRequest();
        assertEquals("access", request.getHeader("Direct"));
    }

    @Test
    @DisplayName("Should support constructor with custom HttpServletRequestMock")
    void shouldSupportConstructorWithCustomMock() {
        HttpServletRequestMock customMock = new HttpServletRequestMock();
        customMock.setHeader("Custom", "value");

        HttpServletRequestResolverMock resolverMock = new HttpServletRequestResolverMock(customMock);

        HttpServletRequest request = resolverMock.resolveHttpServletRequest();
        assertEquals("value", request.getHeader("Custom"));
        assertSame(customMock, resolverMock.getHttpServletRequestMock());
    }

    @Test
    @DisplayName("Should support static factory method for bearer token")
    void shouldSupportStaticFactoryMethodForBearerToken() {
        HttpServletRequestResolverMock bearerMock = HttpServletRequestResolverMock.withBearerToken("token123");

        HttpServletRequest request = bearerMock.resolveHttpServletRequest();

        assertEquals("Bearer token123", request.getHeader("Authorization"));
        assertEquals("GET", request.getMethod());
        assertEquals("/api/protected", request.getRequestURI());
    }

    @Test
    @DisplayName("Should support static factory method for no request context")
    void shouldSupportStaticFactoryMethodForNoRequestContext() {
        HttpServletRequestResolverMock noContextMock = HttpServletRequestResolverMock.withoutRequestContext();

        assertThrows(IllegalStateException.class, noContextMock::resolveHttpServletRequest);
        assertThrows(IllegalStateException.class, noContextMock::resolveHeaderMap);
        assertFalse(noContextMock.isRequestContextAvailable());
    }

    @Test
    @DisplayName("Should support static factory method for request")
    void shouldSupportStaticFactoryMethodForRequest() {
        HttpServletRequestResolverMock requestMock = HttpServletRequestResolverMock.withRequest("POST", "/api/data");

        HttpServletRequest request = requestMock.resolveHttpServletRequest();

        assertEquals("POST", request.getMethod());
        assertEquals("/api/data", request.getRequestURI());
        assertEquals("application/json", request.getHeader("Content-Type"));
        assertEquals("application/json", request.getHeader("Accept"));
    }

    @Test
    @DisplayName("Should support reset functionality")
    void shouldSupportResetFunctionality() {
        mock.setBearerToken("token123")
                .setMethod("POST")
                .setRequestURI("/api/test")
                .setContextPath("/app")
                .setHeader("X-Custom", "value")
                .setRequestContextAvailable(false);

        mock.reset();

        HttpServletRequest request = mock.resolveHttpServletRequest();

        // Should be reset to defaults
        assertEquals("GET", request.getMethod());
        assertEquals("/", request.getRequestURI());
        assertEquals("", request.getContextPath());
        assertNull(request.getHeader("Authorization"));
        assertNull(request.getHeader("X-Custom"));
        assertTrue(mock.isRequestContextAvailable());
    }

    @Test
    @DisplayName("Should support header removal and clearing")
    void shouldSupportHeaderRemovalAndClearing() {
        mock.setHeader("Header1", "value1")
                .setHeader("Header2", "value2")
                .setHeader("Header3", "value3");

        HttpServletRequest request = mock.resolveHttpServletRequest();
        assertNotNull(request.getHeader("Header1"));
        assertNotNull(request.getHeader("Header2"));
        assertNotNull(request.getHeader("Header3"));

        mock.removeHeader("Header2");
        assertNull(request.getHeader("Header2"));
        assertNotNull(request.getHeader("Header1"));
        assertNotNull(request.getHeader("Header3"));

        mock.clearHeaders();
        assertNull(request.getHeader("Header1"));
        assertNull(request.getHeader("Header3"));
    }

    @Test
    @DisplayName("Should maintain state consistency")
    void shouldMaintainStateConsistency() {
        mock.setRequestContextAvailable(true);
        assertTrue(mock.isRequestContextAvailable());
        assertDoesNotThrow(() -> mock.resolveHttpServletRequest());

        mock.setRequestContextAvailable(false);
        assertFalse(mock.isRequestContextAvailable());
        assertThrows(IllegalStateException.class, () -> mock.resolveHttpServletRequest());

        mock.setRequestContextAvailable(true);
        assertTrue(mock.isRequestContextAvailable());
        assertDoesNotThrow(() -> mock.resolveHttpServletRequest());
    }
}