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
import lombok.NonNull;

import java.util.*;

/**
 * Interface for resolving HTTP servlet objects within request contexts.
 *
 * <p>The primary method is {@link #resolveHttpServletRequest()} which returns the
 * current HttpServletRequest. If no request context is available, implementations
 * must throw {@link IllegalStateException}.</p>
 *
 * <p>The {@link #resolveHeaderMap()} method provides a default implementation that
 * extracts headers from the resolved HttpServletRequest.</p>
 *
 * <p><strong>Context Dependency:</strong> When not in an appropriate request context (e.g., outside REST requests),
 * the CDI system will throw {@link jakarta.enterprise.inject.IllegalProductException} because the underlying
 * {@code @RequestScoped} HttpServerRequest producer cannot provide a valid instance. This is wrapped behavior -
 * implementations may throw {@link IllegalStateException} which gets wrapped by CDI.</p>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public interface HttpServletRequestResolver {

    /**
     * Resolves the HttpServletRequest from the current request context.
     *
     * <p>This is the primary method that implementations must provide.</p>
     *
     * @return HttpServletRequest from the current context
     * @throws jakarta.enterprise.inject.IllegalProductException if not in an active request context 
     *                               (CDI wraps underlying exceptions when @RequestScoped producer fails)
     * @throws IllegalStateException if the infrastructure is not available to resolve the request
     */
    @NonNull
    HttpServletRequest resolveHttpServletRequest() throws IllegalStateException;

    /**
     * Resolves HTTP headers from the current request context as a Map.
     *
     * <p>This default implementation extracts headers from the HttpServletRequest
     * resolved by {@link #resolveHttpServletRequest()}.</p>
     *
     * @return Map of HTTP headers from the current context
     * @throws jakarta.enterprise.inject.IllegalProductException if not in an active request context 
     *                               (CDI wraps underlying exceptions when @RequestScoped producer fails)
     * @throws IllegalStateException if the infrastructure is not available to resolve headers
     */
    @NonNull
    default Map<String, List<String>> resolveHeaderMap() throws IllegalStateException {
        return createHeaderMapFromRequest(resolveHttpServletRequest());
    }

    /**
     * Creates a header map from the given HttpServletRequest.
     *
     * <p>This helper method is used by the default implementation of {@link #resolveHeaderMap()}.</p>
     *
     * @param request the HttpServletRequest to extract headers from (must not be null)
     * @return Map of HTTP headers extracted from the request
     * @throws IllegalStateException if headers cannot be extracted from the request
     */
    @NonNull
    default Map<String, List<String>> createHeaderMapFromRequest(@NonNull HttpServletRequest request)
            throws IllegalStateException {

        Map<String, List<String>> headerMap = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();

        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                List<String> headerValues = new ArrayList<>();
                Enumeration<String> values = request.getHeaders(headerName);
                while (values.hasMoreElements()) {
                    headerValues.add(values.nextElement());
                }
                headerMap.put(headerName, headerValues);
            }
        }

        return headerMap;
    }
}