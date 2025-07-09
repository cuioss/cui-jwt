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
package de.cuioss.jwt.quarkus.annotation;

import jakarta.inject.Qualifier;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


import jakarta.enterprise.util.Nonbinding;

/**
 * CDI qualifier for injecting validated AccessTokenContent from HTTP Authorization header.
 * <p>
 * This annotation allows specifying required scopes, roles, and groups that the bearer token
 * must contain for successful injection. If any of the requirements are not met, or if the
 * token is invalid or missing, the producer returns null. Use {@link jakarta.enterprise.inject.Instance}
 * to safely handle cases where the token might not be available.
 * <p>
 * Note: {@code isResolvable()} checks if the bean definition exists (always true for producers),
 * while {@code get() == null} indicates actual validation failure or missing token.
 * <p>
 * Example usage in JAX-RS endpoint:
 * <pre>{@code
 * @Inject
 * @BearerToken(requiredScopes = {"read", "write"}, requiredRoles = {"admin"})
 * private Instance<AccessTokenContent> accessToken;
 * 
 * @GET
 * public Response getData() {
 *     AccessTokenContent token = accessToken.get();
 *     if (token == null) {
 *         return Response.status(401).build(); // Unauthorized
 *     }
 *     // Use validated token
 *     return Response.ok(token.getSubject()).build();
 * }
 * }</pre>
 * <p>
 * The producer validates the bearer token by:
 * <ul>
 *   <li>Extracting the token from the HTTP Authorization header</li>
 *   <li>Validating the JWT signature and claims using the configured TokenValidator</li>
 *   <li>Checking that all required scopes, roles, and groups are present</li>
 *   <li>Returning null if any validation fails</li>
 * </ul>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE})
public @interface BearerToken {

    /**
     * Specifies the required scopes that the bearer token must contain.
     * <p>
     * All listed scopes must be present in the token's scope claim for successful validation.
     * If this array is empty, no scope validation is performed.
     *
     * @return array of required scope names
     */
    @Nonbinding
    String[] requiredScopes() default {};

    /**
     * Specifies the required roles that the bearer token must contain.
     * <p>
     * All listed roles must be present in the token's roles claim for successful validation.
     * If this array is empty, no role validation is performed.
     *
     * @return array of required role names
     */
    @Nonbinding
    String[] requiredRoles() default {};

    /**
     * Specifies the required groups that the bearer token must contain.
     * <p>
     * All listed groups must be present in the token's groups claim for successful validation.
     * If this array is empty, no group validation is performed.
     *
     * @return array of required group names
     */
    @Nonbinding
    String[] requiredGroups() default {};
}