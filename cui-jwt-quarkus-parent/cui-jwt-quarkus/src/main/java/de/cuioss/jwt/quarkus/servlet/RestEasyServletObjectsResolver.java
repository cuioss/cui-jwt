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

import de.cuioss.jwt.quarkus.annotation.ServletObjectsResolver;
import de.cuioss.tools.logging.CuiLogger;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.servlet.http.HttpServletRequest;
import org.jboss.resteasy.spi.ResteasyProviderFactory;

import java.util.Optional;


import jakarta.enterprise.context.ApplicationScoped;

/**
 * RESTEasy-based implementation for resolving servlet objects within RESTEasy request contexts.
 *
 * <p>This implementation uses {@link ResteasyProviderFactory#getContextData(Class)} to access HTTP context.
 * Methods return {@link Optional#empty()} when no RESTEasy context is available, which is
 * the usual case outside of active REST requests.</p>
 *
 * <p><strong>Usage:</strong> This resolver should only be used within active RESTEasy request contexts.
 * Outside of REST requests, all methods will return empty Optional values.</p>
 *
 * <p><strong>CDI Usage:</strong></p>
 * <pre>{@code
 * @Inject
 * @ServletObjectsResolver(ServletObjectsResolver.Variant.RESTEASY)
 * HttpServletRequestResolver resolver;
 * }</pre>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@ApplicationScoped
@ServletObjectsResolver(ServletObjectsResolver.Variant.RESTEASY)
@RegisterForReflection
public class RestEasyServletObjectsResolver implements HttpServletRequestResolver {

    private static final CuiLogger LOGGER = new CuiLogger(RestEasyServletObjectsResolver.class);

    /**
     * Resolves the HttpServletRequest from the current RESTEasy context.
     *
     * @return Optional containing HttpServletRequest from RESTEasy context, or empty if not available.
     *         {@code Optional.isEmpty()} is the usual case when not in an active RESTEasy request.
     */
    @Override
    public Optional<HttpServletRequest> resolveHttpServletRequest() {
        ResteasyProviderFactory providerFactory = ResteasyProviderFactory.getInstance();
        if (providerFactory == null) {
            LOGGER.debug("ResteasyProviderFactory not available");
            return Optional.empty();
        }

        HttpServletRequest request = providerFactory.getContextData(HttpServletRequest.class);
        if (request == null) {
            LOGGER.debug("HttpServletRequest not available - not in RESTEasy context");
            return Optional.empty();
        }
        return Optional.of(request);
    }
}