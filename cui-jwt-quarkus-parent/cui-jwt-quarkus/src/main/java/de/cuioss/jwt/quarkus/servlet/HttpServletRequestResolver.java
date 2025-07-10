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

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.servlet.http.HttpServletRequest;
import lombok.NonNull;

import java.util.*;

/**
 * Interface for resolving HTTP servlet objects within request contexts.
 *
 * <p>The primary method is {@link #resolveHttpServletRequest()} which returns an
 * {@link Optional} containing the current HttpServletRequest or empty if not available.</p>
 *
 * <p>The {@link #resolveHeaderMap()} method provides a default implementation that
 * extracts headers from the resolved HttpServletRequest.</p>
 *
 * <p><strong>Context Dependency:</strong> Implementations should return {@code Optional.empty()}
 * when not in an appropriate request context (e.g., outside REST requests).</p>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@RegisterForReflection
public interface HttpServletRequestResolver {

    /**
     * Resolves the HttpServletRequest from the current request context.
     *
     * <p>This is the primary method that implementations must provide.</p>
     *
     * @return Optional containing HttpServletRequest from the current context, or empty if not available.
     *         {@code Optional.isEmpty()} is the usual case when not in an active request context.
     */
    Optional<HttpServletRequest> resolveHttpServletRequest();

    /**
     * Resolves HTTP headers from the current request context as a Map.
     *
     * <p>This default implementation extracts headers from the HttpServletRequest
     * resolved by {@link #resolveHttpServletRequest()}.</p>
     *
     * @return Optional containing Map of HTTP headers from the current context, or empty if not available.
     *         {@code Optional.isEmpty()} is the usual case when not in an active request context.
     */
    default Optional<Map<String, List<String>>> resolveHeaderMap() {
        return resolveHttpServletRequest().flatMap(this::createHeaderMapFromRequest);
    }

    /**
     * Creates a header map from the given HttpServletRequest.
     *
     * <p>This helper method is used by the default implementation of {@link #resolveHeaderMap()}.</p>
     *
     * @param request the HttpServletRequest to extract headers from
     * @return Optional containing Map of HTTP headers, or empty if headers cannot be extracted
     */
    @NonNull
    default Optional<Map<String, List<String>>> createHeaderMapFromRequest(HttpServletRequest request) {
        if (request == null) {
            return Optional.empty();
        }

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

        return Optional.of(headerMap);
    }
}