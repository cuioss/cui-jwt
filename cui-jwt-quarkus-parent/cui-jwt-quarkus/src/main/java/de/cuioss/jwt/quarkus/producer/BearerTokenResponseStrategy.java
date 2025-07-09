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

import jakarta.ws.rs.core.Response;

/**
 * Strategy interface for creating HTTP responses based on bearer token validation status.
 * <p>
 * This strategy pattern allows each {@link BearerTokenStatus} to have its own specialized
 * response creation logic, following OAuth 2.0 Bearer Token specification (RFC 6750) and
 * OAuth Step-Up Authentication Challenge (draft-ietf-oauth-step-up-authn-challenge-17)
 * best practices.
 * <p>
 * Each implementation should handle:
 * <ul>
 *   <li>Appropriate HTTP status codes</li>
 *   <li>Proper WWW-Authenticate headers</li>
 *   <li>Security headers (Cache-Control, Pragma)</li>
 *   <li>Error descriptions and parameters</li>
 * </ul>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public interface BearerTokenResponseStrategy {

    /**
     * Creates an appropriate HTTP response for the given bearer token validation result.
     *
     * @param result The bearer token validation result containing status and context information
     * @return Response object with appropriate HTTP status code, headers, and body
     */
    Response createResponse(BearerTokenResult result);
}