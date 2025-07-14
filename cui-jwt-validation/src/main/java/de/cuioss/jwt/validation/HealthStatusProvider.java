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
package de.cuioss.jwt.validation;

import de.cuioss.jwt.validation.jwks.LoaderStatus;

/**
 * Common interface for components that provide health status information.
 * <p>
 * This interface unifies health checking across different JWT validation components
 * by providing a consistent way to check component health and retrieve detailed status information.
 * <p>
 * Implementation requirements:
 * <ul>
 *   <li>Implementations must be thread-safe for concurrent access</li>
 *   <li>Health checks should be fail-fast to avoid blocking callers</li>
 *   <li>For lazy-loading implementations, {@link #isHealthy()} may trigger initial loading</li>
 * </ul>
 * <p>
 * Usage example:
 * <pre>
 * HealthStatusProvider provider = someComponent;
 * LoaderStatus status = provider.isHealthy();
 * if (status == LoaderStatus.OK) {
 *     // Component is healthy and ready to use
 * } else if (status == LoaderStatus.ERROR) {
 *     // Handle error state
 * } else {
 *     // Handle undefined state
 * }
 * </pre>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public interface HealthStatusProvider {

    /**
     * Checks the component's health status and returns detailed status information.
     * <p>
     * This method performs a health check by verifying that the component
     * can perform its intended operations and returns the current status:
     * <ul>
     *   <li>{@link LoaderStatus#OK} - Component is operational and healthy</li>
     *   <li>{@link LoaderStatus#ERROR} - Component encountered an error</li>
     *   <li>{@link LoaderStatus#UNDEFINED} - Initial state, not yet initialized</li>
     * </ul>
     * <p>
     * The exact definition of "healthy" depends on the implementation:
     * <ul>
     *   <li>For JWKS loaders: Can access at least one cryptographic key</li>
     *   <li>For well-known resolvers: Can discover and access required endpoints</li>
     *   <li>For issuer configurations: Are enabled and have operational loaders</li>
     * </ul>
     * <p>
     * For components with lazy initialization, this method may trigger the initial
     * loading operation if not already performed.
     *
     * @return the current health status of the component, never {@code null}
     */
    LoaderStatus isHealthy();
}