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

import de.cuioss.jwt.quarkus.CuiJwtQuarkusLogMessages;
import de.cuioss.jwt.quarkus.annotation.ServletObjectsResolver;
import de.cuioss.tools.logging.CuiLogger;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.servlet.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpUpgradeHandler;
import jakarta.servlet.http.Part;
import lombok.NonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import io.vertx.core.net.SocketAddress;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static de.cuioss.jwt.quarkus.CuiJwtQuarkusLogMessages.ERROR;

/**
 * Vertx-based implementation for resolving servlet objects within Quarkus JAX-RS request contexts.
 *
 * <p>This implementation uses Vertx {@link HttpServerRequest} to access HTTP context and provides
 * a comprehensive compatibility layer that creates an HttpServletRequest-like interface from Vertx data.
 * Methods throw {@link IllegalStateException} when no Vertx context is available, which is
 * the usual case outside of active REST requests.</p>
 *
 * <p><strong>Usage:</strong> This resolver should only be used within active Quarkus JAX-RS request contexts.
 * Outside of REST requests, CDI will throw {@link jakarta.enterprise.inject.IllegalProductException} 
 * because the underlying {@code @RequestScoped} HttpServerRequest producer cannot provide a valid instance.</p>
 *
 * <p><strong>CDI Usage:</strong></p>
 * <pre>{@code
 * @Inject
 * @ServletObjectsResolver(ServletObjectsResolver.Variant.VERTX)
 * HttpServletRequestResolver resolver;
 * }</pre>
 *
 * <p><strong>Quarkus Context:</strong> This implementation works with Quarkus's Vertx-based HTTP layer
 * and provides access to HTTP request data through the native Vertx APIs.</p>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@ApplicationScoped
@ServletObjectsResolver(ServletObjectsResolver.Variant.VERTX)
@RegisterForReflection(methods = true, fields = false)
public class VertxServletObjectsResolver implements HttpServletRequestResolver {

    private static final CuiLogger LOGGER = new CuiLogger(VertxServletObjectsResolver.class);

    private final Instance<HttpServerRequest> vertxRequestInstance;

    @Inject
    public VertxServletObjectsResolver(Instance<HttpServerRequest> vertxRequestInstance) {
        this.vertxRequestInstance = vertxRequestInstance;
    }

    /**
     * Resolves the HttpServletRequest from the current Vertx context.
     *
     * <p>This implementation creates a comprehensive HttpServletRequest adapter from the Vertx HttpServerRequest.
     * The adapter provides access to headers, request parameters, and other HTTP request information.</p>
     *
     * @return HttpServletRequest adapter from Vertx context
     * @throws jakarta.enterprise.inject.IllegalProductException if not in an active request context 
     *                               (CDI wraps underlying exceptions when @RequestScoped producer fails)
     * @throws IllegalStateException if CDI context is available but HttpServerRequest is null
     */
    @NonNull
    @Override
    public HttpServletRequest resolveHttpServletRequest() throws IllegalStateException {
        LOGGER.debug("Attempting to resolve HttpServletRequest from Vertx context");
        
        if (vertxRequestInstance.isUnsatisfied()) {
            LOGGER.error(ERROR.VERTX_REQUEST_CONTEXT_UNAVAILABLE.format());
            throw new IllegalStateException("Vertx HttpServerRequest bean is not available in CDI context");
        }
        
        HttpServerRequest vertxRequest = vertxRequestInstance.get();
        
        if (vertxRequest == null) {
            LOGGER.error(ERROR.VERTX_REQUEST_CONTEXT_UNAVAILABLE.format());
            throw new IllegalStateException("Vertx HttpServerRequest is null - no active request context available");
        }
        
        LOGGER.debug("Successfully resolved Vertx HttpServerRequest: %s", vertxRequest.getClass().getName());
        return new VertxHttpServletRequestAdapter(vertxRequest);
    }

    /**
     * Comprehensive HttpServletRequest adapter that provides extensive mapping from Vertx HttpServerRequest.
     * This adapter implements as many HttpServletRequest methods as possible using Vertx data,
     * providing a robust compatibility layer for servlet-based APIs.
     */
    private static class VertxHttpServletRequestAdapter implements HttpServletRequest {

        private final HttpServerRequest vertxRequest;
        private final Map<String, Object> attributes = new ConcurrentHashMap<>();

        VertxHttpServletRequestAdapter(HttpServerRequest vertxRequest) {
            this.vertxRequest = vertxRequest;
        }

        @Override
        public String getHeader(String name) {
            return vertxRequest.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            List<String> headers = new ArrayList<>();
            String header = vertxRequest.getHeader(name);
            if (header != null) {
                headers.add(header);
            }
            return Collections.enumeration(headers);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> headerNames = new HashSet<>();
            vertxRequest.headers().forEach(entry -> headerNames.add(entry.getKey()));
            return Collections.enumeration(headerNames);
        }

        // Extended HttpServletRequest implementation mapping Vertx data where possible

        @Override
        public String getAuthType() {
            throw new UnsupportedOperationException(
                "Authentication type not available in Vertx context - use Quarkus Security instead");
        }

        @Override
        public Cookie[] getCookies() {
            throw new UnsupportedOperationException(
                "Cookie access not supported in minimal Vertx adapter - use RoutingContext for cookie handling");
        }

        @Override
        public long getDateHeader(String name) {
            String header = vertxRequest.getHeader(name);
            if (header == null) {
                return -1;
            }
            try {
                // Basic date parsing - could be enhanced with proper HTTP date parsing
                return Long.parseLong(header);
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        @Override
        public int getIntHeader(String name) {
            String header = vertxRequest.getHeader(name);
            if (header == null) {
                return -1;
            }
            try {
                return Integer.parseInt(header);
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        @Override
        public String getMethod() {
            return vertxRequest.method().name();
        }

        @Override
        public String getPathInfo() {
            throw new UnsupportedOperationException(
                "Path info not available in Vertx context - use JAX-RS @PathParam instead");
        }

        @Override
        public String getPathTranslated() {
            throw new UnsupportedOperationException(
                "Path translation not supported in Vertx adapter");
        }

        @Override
        public String getContextPath() {
            // Try to extract context path from URI if possible
            String uri = vertxRequest.uri();
            if (uri != null && uri.startsWith("/")) {
                int secondSlash = uri.indexOf('/', 1);
                if (secondSlash > 0) {
                    return uri.substring(0, secondSlash);
                }
            }
            return "";
        }

        @Override
        public String getQueryString() {
            return vertxRequest.query();
        }

        @Override
        public String getRemoteUser() {
            throw new UnsupportedOperationException(
                "Remote user not available in Vertx context - use Quarkus Security Context instead");
        }

        @Override
        public boolean isUserInRole(String role) {
            throw new UnsupportedOperationException(
                "Role checking not available in Vertx context - use Quarkus Security Context instead");
        }

        @Override
        public Principal getUserPrincipal() {
            throw new UnsupportedOperationException(
                "User principal not available in Vertx context - use Quarkus Security Context instead");
        }

        @Override
        public String getRequestedSessionId() {
            throw new UnsupportedOperationException(
                "Session ID not available in Vertx context - use Quarkus session management instead");
        }

        @Override
        public String getRequestURI() {
            return vertxRequest.uri();
        }

        @Override
        public StringBuffer getRequestURL() {
            return new StringBuffer(vertxRequest.absoluteURI());
        }

        @Override
        public String getServletPath() {
            // Return the path portion of the URI, excluding query parameters
            String uri = vertxRequest.uri();
            if (uri != null) {
                int queryIndex = uri.indexOf('?');
                return queryIndex > 0 ? uri.substring(0, queryIndex) : uri;
            }
            return "/";
        }

        @Override
        public HttpSession getSession(boolean create) {
            throw new UnsupportedOperationException(
                "HttpSession not supported in Vertx context - use Quarkus session management instead");
        }

        @Override
        public HttpSession getSession() {
            throw new UnsupportedOperationException(
                "HttpSession not supported in Vertx context - use Quarkus session management instead");
        }

        @Override
        public String changeSessionId() {
            throw new UnsupportedOperationException(
                "Session management not supported in Vertx context - use Quarkus session management instead");
        }

        @Override
        public boolean isRequestedSessionIdValid() {
            throw new UnsupportedOperationException(
                "Session validation not supported in Vertx context - use Quarkus session management instead");
        }

        @Override
        public boolean isRequestedSessionIdFromCookie() {
            throw new UnsupportedOperationException(
                "Session tracking not supported in Vertx context - use Quarkus session management instead");
        }

        @Override
        public boolean isRequestedSessionIdFromURL() {
            throw new UnsupportedOperationException(
                "Session tracking not supported in Vertx context - use Quarkus session management instead");
        }

        @Override
        public boolean authenticate(HttpServletResponse response) throws IOException, ServletException {
            throw new UnsupportedOperationException(
                "Authentication not supported in Vertx adapter - use Quarkus Security instead");
        }

        @Override
        public void login(String username, String password) throws ServletException {
            throw new UnsupportedOperationException(
                "Login not supported in Vertx adapter - use Quarkus Security instead");
        }

        @Override
        public void logout() throws ServletException {
            throw new UnsupportedOperationException(
                "Logout not supported in Vertx adapter - use Quarkus Security instead");
        }

        @Override
        public Collection<Part> getParts() throws IOException, ServletException {
            throw new UnsupportedOperationException(
                "Multipart access not supported in minimal Vertx adapter - use RoutingContext with BodyHandler");
        }

        @Override
        public Part getPart(String name) throws IOException, ServletException {
            throw new UnsupportedOperationException(
                "Multipart access not supported in minimal Vertx adapter - use RoutingContext with BodyHandler");
        }

        @Override
        public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) throws IOException, ServletException {
            throw new UnsupportedOperationException(
                "Protocol upgrade not supported in Vertx adapter");
        }

        // ServletRequest methods

        @Override
        public Object getAttribute(String name) {
            return attributes.get(name);
        }

        @Override
        public Enumeration<String> getAttributeNames() {
            return Collections.enumeration(attributes.keySet());
        }

        @Override
        public String getCharacterEncoding() {
            String contentType = getContentType();
            if (contentType != null && contentType.contains("charset=")) {
                int charsetIndex = contentType.indexOf("charset=");
                String charset = contentType.substring(charsetIndex + 8);
                int semicolonIndex = charset.indexOf(';');
                return semicolonIndex > 0 ? charset.substring(0, semicolonIndex).trim() : charset.trim();
            }
            return "UTF-8"; // Default
        }

        @Override
        public void setCharacterEncoding(String env) throws UnsupportedEncodingException {
            throw new UnsupportedOperationException(
                "Character encoding modification not supported in Vertx adapter");
        }

        @Override
        public int getContentLength() {
            String contentLength = vertxRequest.getHeader("Content-Length");
            if (contentLength != null) {
                try {
                    return Integer.parseInt(contentLength);
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
            return -1;
        }

        @Override
        public long getContentLengthLong() {
            String contentLength = vertxRequest.getHeader("Content-Length");
            if (contentLength != null) {
                try {
                    return Long.parseLong(contentLength);
                } catch (NumberFormatException e) {
                    return -1L;
                }
            }
            return -1L;
        }

        @Override
        public String getContentType() {
            return vertxRequest.getHeader("Content-Type");
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            throw new UnsupportedOperationException(
                "Direct stream access not supported - use Vertx async request handling");
        }

        @Override
        public String getParameter(String name) {
            // Extract parameters from query string
            String query = vertxRequest.query();
            if (query != null && !query.isEmpty()) {
                String[] pairs = query.split("&");
                for (String pair : pairs) {
                    String[] keyValue = pair.split("=", 2);
                    if (keyValue.length == 2 && keyValue[0].equals(name)) {
                        try {
                            return java.net.URLDecoder.decode(keyValue[1], "UTF-8");
                        } catch (UnsupportedEncodingException e) {
                            return keyValue[1];
                        }
                    }
                }
            }
            return null;
        }

        @Override
        public Enumeration<String> getParameterNames() {
            Set<String> paramNames = new HashSet<>();
            String query = vertxRequest.query();
            if (query != null && !query.isEmpty()) {
                String[] pairs = query.split("&");
                for (String pair : pairs) {
                    String[] keyValue = pair.split("=", 2);
                    if (keyValue.length >= 1) {
                        paramNames.add(keyValue[0]);
                    }
                }
            }
            return Collections.enumeration(paramNames);
        }

        @Override
        public String[] getParameterValues(String name) {
            List<String> values = new ArrayList<>();
            String query = vertxRequest.query();
            if (query != null && !query.isEmpty()) {
                String[] pairs = query.split("&");
                for (String pair : pairs) {
                    String[] keyValue = pair.split("=", 2);
                    if (keyValue.length == 2 && keyValue[0].equals(name)) {
                        try {
                            values.add(java.net.URLDecoder.decode(keyValue[1], "UTF-8"));
                        } catch (UnsupportedEncodingException e) {
                            values.add(keyValue[1]);
                        }
                    }
                }
            }
            return values.isEmpty() ? null : values.toArray(new String[0]);
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            Map<String, List<String>> paramMap = new HashMap<>();
            String query = vertxRequest.query();
            if (query != null && !query.isEmpty()) {
                String[] pairs = query.split("&");
                for (String pair : pairs) {
                    String[] keyValue = pair.split("=", 2);
                    if (keyValue.length >= 1) {
                        String key = keyValue[0];
                        String value = keyValue.length == 2 ? keyValue[1] : "";
                        try {
                            value = java.net.URLDecoder.decode(value, "UTF-8");
                        } catch (UnsupportedEncodingException e) {
                            // Use original value if decoding fails
                        }
                        paramMap.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
                    }
                }
            }
            
            // Convert to String[] values
            Map<String, String[]> result = new HashMap<>();
            for (Map.Entry<String, List<String>> entry : paramMap.entrySet()) {
                result.put(entry.getKey(), entry.getValue().toArray(new String[0]));
            }
            return result;
        }

        @Override
        public String getProtocol() {
            // Return HTTP version if available
            return "HTTP/1.1"; // Default - could be enhanced to detect actual version
        }

        @Override
        public String getScheme() {
            return vertxRequest.scheme();
        }

        @Override
        public String getServerName() {
            return vertxRequest.host();
        }

        @Override
        public int getServerPort() {
            SocketAddress localAddress = vertxRequest.localAddress();
            return localAddress != null ? localAddress.port() : -1;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            throw new UnsupportedOperationException(
                "Character stream access not supported - use Vertx async request handling");
        }

        @Override
        public String getRemoteAddr() {
            return vertxRequest.remoteAddress().host();
        }

        @Override
        public String getRemoteHost() {
            return vertxRequest.remoteAddress().host();
        }

        @Override
        public void setAttribute(String name, Object o) {
            if (o == null) {
                attributes.remove(name);
            } else {
                attributes.put(name, o);
            }
        }

        @Override
        public void removeAttribute(String name) {
            attributes.remove(name);
        }

        @Override
        public Locale getLocale() {
            String acceptLanguage = vertxRequest.getHeader("Accept-Language");
            if (acceptLanguage != null && !acceptLanguage.isEmpty()) {
                // Parse first locale from Accept-Language header
                String[] languages = acceptLanguage.split(",");
                if (languages.length > 0) {
                    String firstLang = languages[0].trim();
                    // Remove quality value if present (e.g., "en-US;q=0.9")
                    int semicolonIndex = firstLang.indexOf(';');
                    if (semicolonIndex > 0) {
                        firstLang = firstLang.substring(0, semicolonIndex);
                    }
                    try {
                        return Locale.forLanguageTag(firstLang);
                    } catch (IllegalArgumentException e) {
                        // Fall through to default for invalid language tags
                    }
                }
            }
            return Locale.getDefault();
        }

        @Override
        public Enumeration<Locale> getLocales() {
            List<Locale> locales = new ArrayList<>();
            String acceptLanguage = vertxRequest.getHeader("Accept-Language");
            if (acceptLanguage != null && !acceptLanguage.isEmpty()) {
                String[] languages = acceptLanguage.split(",");
                for (String lang : languages) {
                    String trimmedLang = lang.trim();
                    // Remove quality value if present
                    int semicolonIndex = trimmedLang.indexOf(';');
                    if (semicolonIndex > 0) {
                        trimmedLang = trimmedLang.substring(0, semicolonIndex);
                    }
                    try {
                        locales.add(Locale.forLanguageTag(trimmedLang));
                    } catch (IllegalArgumentException e) {
                        // Skip invalid locale tags
                    }
                }
            }
            if (locales.isEmpty()) {
                locales.add(Locale.getDefault());
            }
            return Collections.enumeration(locales);
        }

        @Override
        public boolean isSecure() {
            return "https".equals(vertxRequest.scheme());
        }

        @Override
        public RequestDispatcher getRequestDispatcher(String path) {
            throw new UnsupportedOperationException(
                "Request dispatching not supported in Vertx context - use JAX-RS routing instead");
        }

        @Override
        public int getRemotePort() {
            return vertxRequest.remoteAddress().port();
        }

        @Override
        public String getLocalName() {
            return vertxRequest.localAddress().host();
        }

        @Override
        public String getLocalAddr() {
            return vertxRequest.localAddress().host();
        }

        @Override
        public int getLocalPort() {
            return vertxRequest.localAddress().port();
        }

        @Override
        public ServletContext getServletContext() {
            throw new UnsupportedOperationException(
                "Servlet context not available in Vertx adapter - use CDI application context instead");
        }

        @Override
        public AsyncContext startAsync() throws IllegalStateException {
            throw new UnsupportedOperationException(
                "Servlet async not supported - use Vertx async patterns or reactive streams");
        }

        @Override
        public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) throws IllegalStateException {
            throw new UnsupportedOperationException(
                "Servlet async not supported - use Vertx async patterns or reactive streams");
        }

        @Override
        public boolean isAsyncStarted() {
            return false; // Vertx has its own async model
        }

        @Override
        public boolean isAsyncSupported() {
            return false; // Vertx has its own async model
        }

        @Override
        public AsyncContext getAsyncContext() {
            throw new UnsupportedOperationException(
                "Servlet async not supported - use Vertx async patterns or reactive streams");
        }

        @Override
        public DispatcherType getDispatcherType() {
            return DispatcherType.REQUEST; // Default for direct requests
        }

        @Override
        public String getRequestId() {
            // Could be enhanced to return actual request ID if available from Vertx
            throw new UnsupportedOperationException(
                "Request ID not available in minimal Vertx adapter");
        }

        @Override
        public String getProtocolRequestId() {
            throw new UnsupportedOperationException(
                "Protocol request ID not available in Vertx adapter");
        }

        @Override
        public ServletConnection getServletConnection() {
            throw new UnsupportedOperationException(
                "Servlet connection not available in Vertx adapter");
        }
    }
}