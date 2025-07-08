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
package de.cuioss.jwt.quarkus.producer;

import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.NonNull;

import java.util.Optional;

/**
 * Service for accessing HTTP request context in a Quarkus-native way.
 * Uses CurrentVertxRequest which is the recommended approach for 2025 Quarkus applications.
 * 
 * @author Oliver Wolff
 * @since 1.0
 */
@RequestScoped
public class HttpContextService {

    @Inject
    Instance<CurrentVertxRequest> currentRequestInstance;

    /**
     * Retrieves the Authorization header from the current HTTP request.
     * Uses Quarkus CurrentVertxRequest for safe access to request context.
     *
     * @return Optional containing the Authorization header value, empty if not present or no request context
     */
    @NonNull
    public Optional<String> getAuthorizationHeader() {
        if (!currentRequestInstance.isResolvable()) {
            return Optional.empty();
        }

        RoutingContext context = currentRequestInstance.get().getCurrent();
        if (context == null) {
            return Optional.empty();
        }

        String authHeader = context.request().getHeader("Authorization");
        return Optional.ofNullable(authHeader);
    }

    /**
     * Retrieves any header from the current HTTP request.
     *
     * @param headerName the name of the header to retrieve
     * @return Optional containing the header value, empty if not present or no request context
     */
    @NonNull
    public Optional<String> getHeader(@NonNull String headerName) {
        if (!currentRequestInstance.isResolvable()) {
            return Optional.empty();
        }

        RoutingContext context = currentRequestInstance.get().getCurrent();
        if (context == null) {
            return Optional.empty();
        }

        String headerValue = context.request().getHeader(headerName);
        return Optional.ofNullable(headerValue);
    }
}