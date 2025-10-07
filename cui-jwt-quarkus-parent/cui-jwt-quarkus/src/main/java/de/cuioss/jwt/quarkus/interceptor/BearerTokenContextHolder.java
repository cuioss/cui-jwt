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
package de.cuioss.jwt.quarkus.interceptor;

import de.cuioss.jwt.quarkus.producer.BearerTokenResult;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.RequestScoped;

import java.util.Optional;

/**
 * Request-scoped context holder for validated bearer token results.
 * <p>
 * This holder makes validated bearer token results available to intercepted methods
 * without requiring explicit parameter passing. The token is stored in request scope
 * and automatically cleaned up after the request completes.
 * <p>
 * Primary use case: Allow intercepted methods to access the validated token result
 * that was set by {@link BearerTokenInterceptor}.
 * <p>
 * Usage example:
 * <pre>{@code
 * @RequestScoped
 * public class SecureService {
 *
 *     @Inject
 *     BearerTokenContextHolder contextHolder;
 *
 *     @BearerToken(requiredScopes = {"read"})
 *     public void processData() {
 *         BearerTokenResult result = contextHolder.get()
 *             .orElseThrow(() -> new IllegalStateException("Token not available"));
 *
 *         if (result.isSuccessfullyAuthorized()) {
 *             AccessTokenContent token = result.getAccessTokenContent().get();
 *             // Use token information
 *         }
 *     }
 * }
 * }</pre>
 * <p>
 * <strong>Thread Safety:</strong> Request-scoped ensures thread safety - each request
 * gets its own instance.
 * <p>
 * <strong>Lifecycle:</strong> Automatically cleaned up when request ends. The interceptor
 * also explicitly clears the context after method execution.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@RequestScoped
@RegisterForReflection
public class BearerTokenContextHolder {

    private BearerTokenResult bearerTokenResult;

    /**
     * Gets the current bearer token result from the request context.
     *
     * @return Optional containing the BearerTokenResult if available, empty otherwise
     */
    public Optional<BearerTokenResult> get() {
        return Optional.ofNullable(bearerTokenResult);
    }

    /**
     * Stores the bearer token result in the request context.
     * This method is called by {@link BearerTokenInterceptor} after validation.
     *
     * @param result the bearer token result to store
     */
    void set(BearerTokenResult result) {
        this.bearerTokenResult = result;
    }

    /**
     * Clears the bearer token result from the request context.
     * This method is called by {@link BearerTokenInterceptor} after method execution.
     */
    void clear() {
        this.bearerTokenResult = null;
    }
}
