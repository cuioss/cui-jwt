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

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;
import jakarta.servlet.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpUpgradeHandler;
import jakarta.servlet.http.Part;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Comprehensive HttpServletRequest adapter that provides extensive mapping from Vertx HttpServerRequest.
 *
 * <p>This adapter implements as many HttpServletRequest methods as possible using Vertx data,
 * providing a robust compatibility layer for servlet-based APIs. Methods that cannot be implemented
 * with Vertx data throw {@link UnsupportedOperationException} with descriptive messages indicating
 * the proper Quarkus alternatives.</p>
 *
 * <p><strong>Thread Safety:</strong> This class is thread-safe through the use of {@link ConcurrentHashMap}
 * for attributes and immutable access to the underlying Vertx request.</p>
 *
 * <p><strong>Supported Operations:</strong></p>
 * <ul>
 *   <li>HTTP headers (complete support)</li>
 *   <li>Request URI, URL, method, scheme, protocol version</li>
 *   <li>Query parameters (parsed from query string)</li>
 *   <li>Content length and type</li>
 *   <li>Remote and local address/port information (null-safe)</li>
 *   <li>Server name and port (extracted from Host header or local address)</li>
 *   <li>Locale parsing from Accept-Language header</li>
 *   <li>Request attributes (in-memory storage)</li>
 *   <li>Cookies (converted from Vert.x to Jakarta servlet format)</li>
 *   <li>Request ID generation (basic tracing support)</li>
 *   <li>Path information (servlet path, context path)</li>
 *   <li>Character encoding validation</li>
 * </ul>
 *
 * <p><strong>Unsupported Operations (with alternatives):</strong></p>
 * <ul>
 *   <li>Session management - use Quarkus session management</li>
 *   <li>Authentication - use Quarkus Security</li>
 *   <li>Multipart - use RoutingContext with BodyHandler</li>
 *   <li>Input streams - use Vertx async request handling</li>
 *   <li>Request dispatching - use JAX-RS routing</li>
 *   <li>Async servlet context - use Vertx async patterns</li>
 * </ul>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@RequiredArgsConstructor
public class VertxHttpServletRequestAdapter implements HttpServletRequest {

    @NonNull
    private final HttpServerRequest vertxRequest;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

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
        Set<io.vertx.core.http.Cookie> vertxCookies = vertxRequest.cookies();
        if (vertxCookies == null || vertxCookies.isEmpty()) {
            return null;
        }
        
        return vertxCookies.stream()
            .map(this::convertVertxCookieToServletCookie)
            .toArray(Cookie[]::new);
    }
    
    /**
     * Converts a Vert.x Cookie to a Jakarta Servlet Cookie.
     * 
     * @param vertxCookie the Vert.x cookie to convert
     * @return the equivalent Jakarta servlet cookie
     */
    private Cookie convertVertxCookieToServletCookie(io.vertx.core.http.Cookie vertxCookie) {
        Cookie servletCookie = new Cookie(vertxCookie.getName(), vertxCookie.getValue());
        
        if (vertxCookie.getDomain() != null) {
            servletCookie.setDomain(vertxCookie.getDomain());
        }
        
        if (vertxCookie.getPath() != null) {
            servletCookie.setPath(vertxCookie.getPath());
        }
        
        servletCookie.setMaxAge((int) vertxCookie.getMaxAge());
        servletCookie.setSecure(vertxCookie.isSecure());
        servletCookie.setHttpOnly(vertxCookie.isHttpOnly());
        
        return servletCookie;
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
        // Path info is typically the extra path information beyond the servlet path
        // In Vert.x, we can extract this from the full path if we know the context
        // For now, return null as it's not clearly defined without servlet context
        return null;
    }

    @Override
    public String getPathTranslated() {
        throw new UnsupportedOperationException(
            "Path translation not supported in Vertx adapter");
    }

    @Override
    public String getContextPath() {
        // In Vert.x, there's no direct equivalent to servlet context path
        // Return empty string as the default since we're not in a servlet container
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
        // In Vert.x, this is essentially the request path
        String path = vertxRequest.path();
        return path != null ? path : "/";
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
        // Character encoding should be set before reading request body
        // Since Vert.x handles encoding differently, we'll validate but not store
        if (env != null) {
            try {
                java.nio.charset.Charset.forName(env);
            } catch (java.nio.charset.UnsupportedCharsetException e) {
                throw new UnsupportedEncodingException("Unsupported encoding: " + env);
            }
        }
        // Note: This doesn't actually change the encoding for the request body
        // as that's handled by Vert.x's async body handling
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
        // Return HTTP version from Vert.x request
        io.vertx.core.http.HttpVersion version = vertxRequest.version();
        return version != null ? version.name().replace('_', '/') : "HTTP/1.1";
    }

    @Override
    public String getScheme() {
        return vertxRequest.scheme();
    }

    @Override
    public String getServerName() {
        String host = vertxRequest.host();
        if (host != null) {
            // Remove port if present
            int colonIndex = host.indexOf(':');
            return colonIndex > 0 ? host.substring(0, colonIndex) : host;
        }
        return "localhost";
    }

    @Override
    public int getServerPort() {
        // First try to get port from Host header
        String host = vertxRequest.host();
        if (host != null) {
            int colonIndex = host.indexOf(':');
            if (colonIndex > 0) {
                try {
                    return Integer.parseInt(host.substring(colonIndex + 1));
                } catch (NumberFormatException e) {
                    // Fall through to local address
                }
            }
        }
        
        // Fall back to local address
        SocketAddress localAddress = vertxRequest.localAddress();
        if (localAddress != null) {
            return localAddress.port();
        }
        
        // Default ports based on scheme
        return "https".equals(vertxRequest.scheme()) ? 443 : 80;
    }

    @Override
    public BufferedReader getReader() throws IOException {
        throw new UnsupportedOperationException(
            "Character stream access not supported - use Vertx async request handling");
    }

    @Override
    public String getRemoteAddr() {
        SocketAddress remoteAddress = vertxRequest.remoteAddress();
        return remoteAddress != null ? remoteAddress.host() : "unknown";
    }

    @Override
    public String getRemoteHost() {
        SocketAddress remoteAddress = vertxRequest.remoteAddress();
        return remoteAddress != null ? remoteAddress.host() : "unknown";
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
        SocketAddress remoteAddress = vertxRequest.remoteAddress();
        return remoteAddress != null ? remoteAddress.port() : -1;
    }

    @Override
    public String getLocalName() {
        SocketAddress localAddress = vertxRequest.localAddress();
        return localAddress != null ? localAddress.host() : "localhost";
    }

    @Override
    public String getLocalAddr() {
        SocketAddress localAddress = vertxRequest.localAddress();
        return localAddress != null ? localAddress.host() : "127.0.0.1";
    }

    @Override
    public int getLocalPort() {
        SocketAddress localAddress = vertxRequest.localAddress();
        return localAddress != null ? localAddress.port() : -1;
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
        // Generate a simple request ID based on connection info and timestamp
        // This provides basic tracing capability without full OpenTracing integration
        return String.format("vertx-req-%d-%s", 
            System.currentTimeMillis(), 
            Integer.toHexString(vertxRequest.hashCode()));
    }

    @Override
    public String getProtocolRequestId() {
        // Return the same as request ID since Vert.x doesn't distinguish these
        return getRequestId();
    }

    @Override
    public ServletConnection getServletConnection() {
        throw new UnsupportedOperationException(
            "Servlet connection not available in Vertx adapter");
    }
}