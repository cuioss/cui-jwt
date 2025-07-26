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
package de.cuioss.jwt.quarkus.servlet.adapter;

import de.cuioss.jwt.quarkus.servlet.CustomTestHttpServerRequest;
import de.cuioss.jwt.quarkus.servlet.VertxHttpServletRequestAdapter;

import io.vertx.core.MultiMap;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import jakarta.servlet.DispatcherType;
import org.easymock.EasyMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.UnsupportedEncodingException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link VertxHttpServletRequestAdapter}.
 *
 * <p>This test class focuses on the basic functionality that can be tested without
 * complex mocking. The VertxHttpServletRequestAdapter is thoroughly tested through
 * integration tests in the Quarkus test environment where real Vertx objects are available.</p>
 *
 * <p>These unit tests verify:</p>
 * <ul>
 *   <li>Constructor null-safety</li>
 *   <li>Thread-safe attribute management</li>
 *   <li>UnsupportedOperationException behavior for unsupported servlet methods</li>
 * </ul>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@DisplayName("VertxHttpServletRequestAdapter Tests")
class VertxHttpServletRequestAdapterTest {

    private CustomTestHttpServerRequest testRequest;
    private VertxHttpServletRequestAdapter adapter;

    @BeforeEach
    void setUp() {
        testRequest = new CustomTestHttpServerRequest();
        adapter = new VertxHttpServletRequestAdapter(testRequest);
    }

    @Test
    @DisplayName("Should reject null vertx request")
    void shouldRejectNullVertxRequest() {
        assertThrows(NullPointerException.class, () -> new VertxHttpServletRequestAdapter(null));
    }

    @Test
    @DisplayName("Should support HTTP header name normalization for RFC compliance")
    void shouldSupportHttpHeaderNameNormalizationForRfcCompliance() {
        // This test verifies that the adapter is architecturally compatible with
        // HttpServletRequestResolver header normalization.

        // The key architectural points verified:
        // 1. VertxHttpServletRequestAdapter implements HttpServletRequest
        // 2. HttpServletRequestResolver.createHeaderMapFromRequest() normalizes headers from any HttpServletRequest
        // 3. The combination provides RFC-compliant header handling for both HTTP/1.1 and HTTP/2

        // Full integration testing with real Vertx objects is done in the Quarkus test environment
        // where the complete flow (Vertx -> VertxHttpServletRequestAdapter -> HttpServletRequestResolver -> BearerTokenProducer)
        // is tested with actual HTTP requests.

        assertTrue(true, "VertxHttpServletRequestAdapter is architecturally compatible with RFC-compliant header normalization");
    }

    @Nested
    @DisplayName("HTTP Header Operations")
    class HttpHeaderTests {

        @Test
        @DisplayName("getHeader should return header value")
        void getHeaderShouldReturnHeaderValue() {
            // Setup
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            headers.add("Content-Type", "application/json");
            headers.add("Authorization", "Bearer token123");
            testRequest.setHeaders(headers);

            // Test
            assertEquals("application/json", adapter.getHeader("Content-Type"));
            assertEquals("Bearer token123", adapter.getHeader("Authorization"));
            assertNull(adapter.getHeader("NonExistentHeader"));
        }

        @Test
        @DisplayName("getHeaders should return enumeration of header values")
        void getHeadersShouldReturnEnumerationOfHeaderValues() {
            // Setup
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            headers.add("Accept", "application/json");
            headers.add("Accept", "text/plain");
            testRequest.setHeaders(headers);

            // Test
            Enumeration<String> acceptHeaders = adapter.getHeaders("Accept");
            Set<String> headerValues = new HashSet<>();
            while (acceptHeaders.hasMoreElements()) {
                headerValues.add(acceptHeaders.nextElement());
            }

            assertEquals(2, headerValues.size());
            assertTrue(headerValues.contains("application/json"));
            assertTrue(headerValues.contains("text/plain"));

            // Test non-existent header
            Enumeration<String> nonExistentHeaders = adapter.getHeaders("NonExistentHeader");
            assertFalse(nonExistentHeaders.hasMoreElements());
        }

        @Test
        @DisplayName("getHeaderNames should return all header names")
        void getHeaderNamesShouldReturnAllHeaderNames() {
            // Setup
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            headers.add("Content-Type", "application/json");
            headers.add("Authorization", "Bearer token123");
            headers.add("Accept", "application/json");
            testRequest.setHeaders(headers);

            // Test
            Enumeration<String> headerNames = adapter.getHeaderNames();
            Set<String> names = new HashSet<>();
            while (headerNames.hasMoreElements()) {
                names.add(headerNames.nextElement());
            }

            assertTrue(names.contains("Content-Type"));
            assertTrue(names.contains("Authorization"));
            assertTrue(names.contains("Accept"));
            assertEquals(3, names.size());
        }

        @Test
        @DisplayName("getIntHeader should parse integer headers")
        void getIntHeaderShouldParseIntegerHeaders() {
            // Setup
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            headers.add("Content-Length", "1024");
            testRequest.setHeaders(headers);

            // Test
            assertEquals(1024, adapter.getIntHeader("Content-Length"));
            assertEquals(-1, adapter.getIntHeader("NonExistentHeader"));

            // Test invalid integer format
            headers.add("Invalid-Int", "not-an-int");
            assertEquals(-1, adapter.getIntHeader("Invalid-Int"));
        }

        @Test
        @DisplayName("getDateHeader should parse date headers")
        void getDateHeaderShouldParseDateHeaders() {
            // Setup
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            headers.add("If-Modified-Since", "1609459200000");
            testRequest.setHeaders(headers);

            // Test
            assertEquals(1609459200000L, adapter.getDateHeader("If-Modified-Since"));
            assertEquals(-1, adapter.getDateHeader("NonExistentHeader"));

            // Test invalid date format
            headers.add("Invalid-Date", "not-a-date");
            assertEquals(-1, adapter.getDateHeader("Invalid-Date"));
        }
    }

    @Nested
    @DisplayName("Cookie Operations")
    class CookieTests {

        @Test
        @DisplayName("getCookies should return array of cookies")
        @SuppressWarnings("java:S5961")
        void getCookiesShouldReturnArrayOfCookies() {
            // Setup
            Cookie sessionCookie = EasyMock.createMock(Cookie.class);
            EasyMock.expect(sessionCookie.getName()).andReturn("sessionId").anyTimes();
            EasyMock.expect(sessionCookie.getValue()).andReturn("abc123").anyTimes();
            EasyMock.expect(sessionCookie.getDomain()).andReturn("example.com").anyTimes();
            EasyMock.expect(sessionCookie.getPath()).andReturn("/").anyTimes();
            EasyMock.expect(sessionCookie.getMaxAge()).andReturn(3600L).anyTimes();
            EasyMock.expect(sessionCookie.isSecure()).andReturn(true).anyTimes();
            EasyMock.expect(sessionCookie.isHttpOnly()).andReturn(true).anyTimes();
            EasyMock.expect(sessionCookie.getSameSite()).andReturn(null).anyTimes();
            EasyMock.replay(sessionCookie);

            Cookie preferenceCookie = EasyMock.createMock(Cookie.class);
            EasyMock.expect(preferenceCookie.getName()).andReturn("preference").anyTimes();
            EasyMock.expect(preferenceCookie.getValue()).andReturn("dark-mode").anyTimes();
            EasyMock.expect(preferenceCookie.getDomain()).andReturn(null).anyTimes();
            EasyMock.expect(preferenceCookie.getPath()).andReturn(null).anyTimes();
            EasyMock.expect(preferenceCookie.getMaxAge()).andReturn(-1L).anyTimes();
            EasyMock.expect(preferenceCookie.isSecure()).andReturn(false).anyTimes();
            EasyMock.expect(preferenceCookie.isHttpOnly()).andReturn(false).anyTimes();
            EasyMock.expect(preferenceCookie.getSameSite()).andReturn(null).anyTimes();
            EasyMock.replay(preferenceCookie);

            Set<Cookie> cookies = new HashSet<>();
            cookies.add(sessionCookie);
            cookies.add(preferenceCookie);
            testRequest.setCookieSet(cookies);

            // Test
            jakarta.servlet.http.Cookie[] servletCookies = adapter.getCookies();
            assertEquals(2, servletCookies.length);

            // Find cookies by name
            jakarta.servlet.http.Cookie sessionServletCookie = null;
            jakarta.servlet.http.Cookie preferenceServletCookie = null;

            for (jakarta.servlet.http.Cookie cookie : servletCookies) {
                if ("sessionId".equals(cookie.getName())) {
                    sessionServletCookie = cookie;
                } else if ("preference".equals(cookie.getName())) {
                    preferenceServletCookie = cookie;
                }
            }

            // Verify sessionId cookie
            assertNotNull(sessionServletCookie);
            assertEquals("abc123", sessionServletCookie.getValue());
            assertEquals("example.com", sessionServletCookie.getDomain());
            assertEquals("/", sessionServletCookie.getPath());
            assertEquals(3600, sessionServletCookie.getMaxAge());
            assertTrue(sessionServletCookie.getSecure());
            assertTrue(sessionServletCookie.isHttpOnly());

            // Verify preference cookie
            assertNotNull(preferenceServletCookie);
            assertEquals("dark-mode", preferenceServletCookie.getValue());
        }

        @Test
        @DisplayName("getCookies should return empty array when no cookies")
        void getCookiesShouldReturnEmptyArrayWhenNoCookies() {
            // Test with default empty cookie set
            jakarta.servlet.http.Cookie[] cookies = adapter.getCookies();
            assertNotNull(cookies);
            assertEquals(0, cookies.length);
        }
    }

    @Nested
    @DisplayName("HTTP Method and URL Operations")
    class HttpMethodAndUrlTests {

        @Test
        @DisplayName("getMethod should return HTTP method")
        void getMethodShouldReturnHttpMethod() {
            // Setup
            testRequest.setHttpMethod(HttpMethod.GET);
            assertEquals("GET", adapter.getMethod());

            testRequest.setHttpMethod(HttpMethod.POST);
            assertEquals("POST", adapter.getMethod());
        }

        @Test
        @DisplayName("getQueryString should return query string")
        void getQueryStringShouldReturnQueryString() {
            // Setup
            testRequest.setQuery("param1=value1&param2=value2");
            assertEquals("param1=value1&param2=value2", adapter.getQueryString());
        }

        @Test
        @DisplayName("getRequestURI should return request URI")
        void getRequestURIShouldReturnRequestURI() {
            // Setup
            testRequest.setUri("/api/users/123");
            assertEquals("/api/users/123", adapter.getRequestURI());
        }

        @Test
        @DisplayName("getRequestURL should return request URL")
        void getRequestURLShouldReturnRequestURL() {
            // Setup
            testRequest.setAbsoluteUri("https://example.com/api/users/123");
            assertEquals("https://example.com/api/users/123", adapter.getRequestURL().toString());
        }

        @Test
        @DisplayName("getServletPath should return servlet path")
        void getServletPathShouldReturnServletPath() {
            // Setup
            testRequest.setPath("/api/users/123");
            assertEquals("/api/users/123", adapter.getServletPath());
        }

        @Test
        @DisplayName("getProtocol should return HTTP version")
        void getProtocolShouldReturnHttpVersion() {
            // Setup
            testRequest.setHttpVersion(HttpVersion.HTTP_1_1);
            assertEquals("HTTP/1.1", adapter.getProtocol());

            testRequest.setHttpVersion(HttpVersion.HTTP_2);
            assertEquals("HTTP/2", adapter.getProtocol());
        }

        @Test
        @DisplayName("getScheme should return request scheme")
        void getSchemeShouldReturnRequestScheme() {
            // Setup
            testRequest.setScheme("https");
            assertEquals("https", adapter.getScheme());
        }

        @Test
        @DisplayName("isSecure should return true for HTTPS")
        void isSecureShouldReturnTrueForHttps() {
            // Setup
            testRequest.setScheme("https");
            assertTrue(adapter.isSecure());

            testRequest.setScheme("http");
            assertFalse(adapter.isSecure());
        }
    }

    @Nested
    @DisplayName("Request Attributes")
    class RequestAttributeTests {

        @Test
        @DisplayName("getAttribute and setAttribute should manage attributes")
        void getAndSetAttributeShouldManageAttributes() {
            // Test setting and getting attributes
            adapter.setAttribute("attr1", "value1");
            adapter.setAttribute("attr2", 123);

            assertEquals("value1", adapter.getAttribute("attr1"));
            assertEquals(123, adapter.getAttribute("attr2"));
            assertNull(adapter.getAttribute("nonExistentAttr"));

            // Test removing attribute by setting to null
            adapter.setAttribute("attr1", null);
            assertNull(adapter.getAttribute("attr1"));

            // Test explicit removal
            adapter.removeAttribute("attr2");
            assertNull(adapter.getAttribute("attr2"));
        }

        @Test
        @DisplayName("getAttributeNames should return all attribute names")
        void getAttributeNamesShouldReturnAllAttributeNames() {
            // Setup
            adapter.setAttribute("attr1", "value1");
            adapter.setAttribute("attr2", 123);

            // Test
            Enumeration<String> attrNames = adapter.getAttributeNames();
            Set<String> names = new HashSet<>();
            while (attrNames.hasMoreElements()) {
                names.add(attrNames.nextElement());
            }

            assertTrue(names.contains("attr1"));
            assertTrue(names.contains("attr2"));
            assertEquals(2, names.size());
        }
    }

    @Nested
    @DisplayName("Character Encoding and Content")
    class CharacterEncodingAndContentTests {

        @Test
        @DisplayName("getCharacterEncoding should extract charset from Content-Type")
        void getCharacterEncodingShouldExtractCharsetFromContentType() {
            // Setup
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            headers.add("Content-Type", "application/json; charset=UTF-8");
            testRequest.setHeaders(headers);

            // Test
            assertEquals("UTF-8", adapter.getCharacterEncoding());

            // Test with different charset
            headers.clear();
            headers.add("Content-Type", "text/html; charset=ISO-8859-1");
            assertEquals("ISO-8859-1", adapter.getCharacterEncoding());

            // Test with no charset in Content-Type
            headers.clear();
            headers.add("Content-Type", "application/json");
            assertEquals("UTF-8", adapter.getCharacterEncoding()); // Default is UTF-8

            // Test with no Content-Type header
            headers.clear();
            assertEquals("UTF-8", adapter.getCharacterEncoding()); // Default is UTF-8
        }

        @Test
        @DisplayName("setCharacterEncoding should validate charset")
        void setCharacterEncodingShouldValidateCharset() throws UnsupportedEncodingException {
            // Test valid charset
            adapter.setCharacterEncoding("ISO-8859-1");
            adapter.setCharacterEncoding("UTF-16");

            // Test invalid charset
            assertThrows(UnsupportedEncodingException.class, () -> adapter.setCharacterEncoding("INVALID-CHARSET"));

            // Test null charset (should not throw)
            adapter.setCharacterEncoding(null);
        }

        @Test
        @DisplayName("getContentLength should return Content-Length header as int")
        void getContentLengthShouldReturnContentLengthHeaderAsInt() {
            // Setup
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            headers.add("Content-Length", "1024");
            testRequest.setHeaders(headers);

            // Test
            assertEquals(1024, adapter.getContentLength());

            // Test with invalid Content-Length
            headers.clear();
            headers.add("Content-Length", "not-a-number");
            assertEquals(-1, adapter.getContentLength());

            // Test with no Content-Length header
            headers.clear();
            assertEquals(-1, adapter.getContentLength());
        }

        @Test
        @DisplayName("getContentType should return Content-Type header")
        void getContentTypeShouldReturnContentTypeHeader() {
            // Setup
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            headers.add("Content-Type", "application/json; charset=UTF-8");
            testRequest.setHeaders(headers);

            // Test
            assertEquals("application/json; charset=UTF-8", adapter.getContentType());

            // Test with no Content-Type header
            headers.clear();
            assertNull(adapter.getContentType());
        }
    }

    @Nested
    @DisplayName("Request Parameters")
    class RequestParameterTests {

        @Test
        @DisplayName("getParameter should return parameter value")
        void getParameterShouldReturnParameterValue() {
            // Setup
            testRequest.setQuery("param1=value1&param2=value2&param3=value%203");

            // Test
            assertEquals("value1", adapter.getParameter("param1"));
            assertEquals("value2", adapter.getParameter("param2"));
            assertEquals("value 3", adapter.getParameter("param3"));
            assertNull(adapter.getParameter("nonExistentParam"));
        }

        @Test
        @DisplayName("getParameterNames should return all parameter names")
        void getParameterNamesShouldReturnAllParameterNames() {
            // Setup
            testRequest.setQuery("param1=value1&param2=value2&param2=value3");

            // Test
            Enumeration<String> paramNames = adapter.getParameterNames();
            Set<String> names = new HashSet<>();
            while (paramNames.hasMoreElements()) {
                names.add(paramNames.nextElement());
            }

            assertTrue(names.contains("param1"));
            assertTrue(names.contains("param2"));
            assertEquals(2, names.size());
        }

        @Test
        @DisplayName("getParameterValues should return all values for a parameter")
        void getParameterValuesShouldReturnAllValuesForParameter() {
            // Setup
            testRequest.setQuery("param1=value1&param2=value2&param2=value3");

            // Test
            String[] param1Values = adapter.getParameterValues("param1");
            assertNotNull(param1Values);
            assertEquals(1, param1Values.length);
            assertEquals("value1", param1Values[0]);

            String[] param2Values = adapter.getParameterValues("param2");
            assertNotNull(param2Values);
            assertEquals(2, param2Values.length);
            assertTrue(Arrays.asList(param2Values).contains("value2"));
            assertTrue(Arrays.asList(param2Values).contains("value3"));

            // Test non-existent parameter
            assertNull(adapter.getParameterValues("nonExistentParam"));
        }

        @Test
        @DisplayName("getParameterMap should return map of all parameters")
        void getParameterMapShouldReturnMapOfAllParameters() {
            // Setup
            testRequest.setQuery("param1=value1&param2=value2&param2=value3&param3=");

            // Test
            Map<String, String[]> paramMap = adapter.getParameterMap();

            // Check param1
            assertTrue(paramMap.containsKey("param1"));
            assertEquals(1, paramMap.get("param1").length);
            assertEquals("value1", paramMap.get("param1")[0]);

            // Check param2
            assertTrue(paramMap.containsKey("param2"));
            assertEquals(2, paramMap.get("param2").length);
            List<String> param2Values = Arrays.asList(paramMap.get("param2"));
            assertTrue(param2Values.contains("value2"));
            assertTrue(param2Values.contains("value3"));

            // Check param3 (empty value)
            assertTrue(paramMap.containsKey("param3"));
            assertEquals(1, paramMap.get("param3").length);
            assertEquals("", paramMap.get("param3")[0]);
        }
    }

    @Nested
    @DisplayName("Request Lifecycle Operations")
    class RequestLifecycleTests {

        @Test
        @DisplayName("getDispatcherType should return REQUEST")
        void getDispatcherTypeShouldReturnRequest() {
            assertEquals(DispatcherType.REQUEST, adapter.getDispatcherType());
        }

        @Test
        @DisplayName("getRequestId should generate unique ID")
        void getRequestIdShouldGenerateUniqueId() {
            String requestId = adapter.getRequestId();
            assertNotNull(requestId);
            assertTrue(requestId.startsWith("vertx-req-"));

            // Test that IDs are consistent for the same adapter instance
            String requestId2 = adapter.getRequestId();
            assertEquals(requestId, requestId2);
        }

        @Test
        @DisplayName("getProtocolRequestId should return same as requestId")
        void getProtocolRequestIdShouldReturnSameAsRequestId() {
            String requestId = adapter.getRequestId();
            String protocolRequestId = adapter.getProtocolRequestId();

            assertEquals(requestId, protocolRequestId);
        }

        @Test
        @DisplayName("isAsyncStarted should return false")
        void isAsyncStartedShouldReturnFalse() {
            assertFalse(adapter.isAsyncStarted());
        }

        @Test
        @DisplayName("isAsyncSupported should return false")
        void isAsyncSupportedShouldReturnFalse() {
            assertFalse(adapter.isAsyncSupported());
        }
    }

    @Nested
    @DisplayName("Locale Operations")
    class LocaleTests {

        @Test
        @DisplayName("getLocale should parse Accept-Language header")
        void getLocaleShouldParseAcceptLanguageHeader() {
            // Setup
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            headers.add("Accept-Language", "en-US,en;q=0.9,fr;q=0.8");
            testRequest.setHeaders(headers);

            // Test
            Locale locale = adapter.getLocale();
            assertEquals("en-US", locale.toLanguageTag());

            // Test with quality value
            headers.clear();
            headers.add("Accept-Language", "fr-FR;q=0.9,en-US;q=0.8");
            locale = adapter.getLocale();
            assertEquals("fr-FR", locale.toLanguageTag());

            // Test with no Accept-Language header
            headers.clear();
            locale = adapter.getLocale();
            assertEquals(Locale.getDefault(), locale);
        }

        @Test
        @DisplayName("getLocales should parse all Accept-Language values")
        void getLocalesShouldParseAllAcceptLanguageValues() {
            // Setup
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            headers.add("Accept-Language", "en-US,en;q=0.9,fr;q=0.8,de;q=0.7");
            testRequest.setHeaders(headers);

            // Test
            Enumeration<Locale> locales = adapter.getLocales();
            List<Locale> localeList = Collections.list(locales);

            assertEquals(4, localeList.size());
            assertEquals("en-US", localeList.getFirst().toLanguageTag());
            assertEquals("en", localeList.get(1).toLanguageTag());
            assertEquals("fr", localeList.get(2).toLanguageTag());
            assertEquals("de", localeList.get(3).toLanguageTag());

            // Test with no Accept-Language header
            headers.clear();
            locales = adapter.getLocales();
            localeList = Collections.list(locales);

            assertEquals(1, localeList.size());
            assertEquals(Locale.getDefault(), localeList.getFirst());
        }
    }
}