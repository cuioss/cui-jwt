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

import de.cuioss.jwt.quarkus.annotation.BearerAuth;
import de.cuioss.jwt.quarkus.producer.BearerTokenProducer;
import de.cuioss.jwt.quarkus.producer.BearerTokenResult;
import de.cuioss.tools.logging.CuiLogger;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import java.util.Collections;
import java.util.Set;

import static de.cuioss.jwt.quarkus.CuiJwtQuarkusLogMessages.WARN.BEARER_TOKEN_ANNOTATION_NOT_FOUND;

/**
 * Interceptor for declarative Bearer token validation at method level.
 * <p>
 * This interceptor provides automatic Bearer token validation and error handling
 * when methods or classes are annotated with {@link BearerAuth}. It follows
 * Quarkus 2025 best practices for security interceptors.
 * <p>
 * The interceptor:
 * <ul>
 *   <li>Extracts annotation parameters (requiredScopes, requiredRoles, requiredGroups)</li>
 *   <li>Delegates validation to {@link BearerTokenProducer}</li>
 *   <li>Returns automatic error responses for failed validation (if method returns Response)</li>
 *   <li>Proceeds with method execution if validation succeeds</li>
 *   <li>Makes validated token available via CDI event for intercepted methods</li>
 * </ul>
 * <p>
 * Usage example:
 * <pre>{@code
 * @Path("/api")
 * public class SecureResource {
 *
 *     @GET
 *     @BearerAuth(requiredScopes = {"read"}, requiredRoles = {"user"})
 *     public Response getData() {
 *         // Only business logic - security handled by interceptor
 *         return Response.ok(data).build();
 *     }
 * }
 * }</pre>
 * <p>
 * <strong>Priority:</strong> PLATFORM_BEFORE + 200 ensures security validation
 * runs after platform infrastructure but before business logic interceptors.
 * <p>
 * <strong>Performance:</strong> Minimal object allocation, reuses existing
 * BearerTokenProducer infrastructure, fast-path execution for successful validation.
 * <p>
 * <strong>Return Type Handling:</strong>
 * <ul>
 *   <li>Response return type: Automatic error response creation via BearerTokenResult</li>
 *   <li>Other return types: Proceed with validation, method can access validated token via CDI event</li>
 * </ul>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@BearerAuth
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 200)
@RegisterForReflection
public class BearerTokenInterceptor {

    private static final CuiLogger LOGGER = new CuiLogger(BearerTokenInterceptor.class);

    private final BearerTokenProducer bearerTokenProducer;
    private final BearerTokenContextHolder contextHolder;

    @Inject
    public BearerTokenInterceptor(BearerTokenProducer bearerTokenProducer,
            BearerTokenContextHolder contextHolder) {
        this.bearerTokenProducer = bearerTokenProducer;
        this.contextHolder = contextHolder;
    }

    /**
     * Intercepts method calls to validate bearer tokens declaratively.
     * <p>
     * This method follows the interceptor pattern for security validation:
     * <ol>
     *   <li>Extract annotation parameters from method or class level</li>
     *   <li>Delegate to BearerTokenProducer for validation</li>
     *   <li>If validation fails and method returns Response, return error response</li>
     *   <li>If validation succeeds, store token in context and proceed</li>
     * </ol>
     *
     * @param ctx the invocation context containing method and annotation information
     * @return the result of the intercepted method, or an error response if validation fails
     * @throws Exception if the intercepted method throws an exception
     */
    @AroundInvoke
    public Object validateBearerToken(InvocationContext ctx) throws Exception {
        LOGGER.trace("BearerTokenInterceptor invoked for method: %s", ctx.getMethod().getName());

        // Extract annotation from method or class level
        BearerAuth annotation = extractAnnotation(ctx);
        if (annotation == null) {
            LOGGER.warn(BEARER_TOKEN_ANNOTATION_NOT_FOUND, ctx.getMethod().getName());
            return ctx.proceed();
        }

        // Extract requirements from annotation
        Set<String> requiredScopes = annotation.requiredScopes().length > 0
                ? Set.of(annotation.requiredScopes())
                : Collections.emptySet();
        Set<String> requiredRoles = annotation.requiredRoles().length > 0
                ? Set.of(annotation.requiredRoles())
                : Collections.emptySet();
        Set<String> requiredGroups = annotation.requiredGroups().length > 0
                ? Set.of(annotation.requiredGroups())
                : Collections.emptySet();

        LOGGER.debug("Validating bearer token with scopes: %s, roles: %s, groups: %s",
                requiredScopes, requiredRoles, requiredGroups);

        // Delegate validation to BearerTokenProducer
        BearerTokenResult result = bearerTokenProducer.getBearerTokenResult(
                requiredScopes, requiredRoles, requiredGroups);

        // Handle validation failure
        if (result.isNotSuccessfullyAuthorized()) {
            LOGGER.debug("Bearer token validation failed: %s", result.getStatus());

            // Always return error response on validation failure
            // Methods must return Response or a compatible type
            LOGGER.trace("Returning automatic error response for failed validation");
            contextHolder.set(result);
            return result.createErrorResponse();
        }

        // Validation successful - store token in context and proceed
        LOGGER.trace("Bearer token validation successful - proceeding with method execution");
        contextHolder.set(result);
        try {
            return ctx.proceed();
        } finally {
            // Clean up context after method execution
            contextHolder.clear();
        }
    }

    /**
     * Extracts the BearerAuth annotation from the invocation context.
     * Checks both method level and class level annotations (method takes precedence).
     *
     * @param ctx the invocation context
     * @return the BearerAuth annotation, or null if not found
     */
    private BearerAuth extractAnnotation(InvocationContext ctx) {
        // Check method level first
        BearerAuth annotation = ctx.getMethod().getAnnotation(BearerAuth.class);
        if (annotation != null) {
            return annotation;
        }

        // Fallback to class level
        return ctx.getTarget().getClass().getAnnotation(BearerAuth.class);
    }
}
