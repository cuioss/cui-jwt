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

/**
 * Interceptor-based Bearer token validation for declarative security.
 * <p>
 * This package provides CDI interceptor support for automatic Bearer token validation
 * at method level using the {@link de.cuioss.jwt.quarkus.annotation.BearerToken} annotation.
 * <p>
 * Key components:
 * <ul>
 *   <li>{@link de.cuioss.jwt.quarkus.interceptor.BearerTokenInterceptor} - Main interceptor for validation</li>
 *   <li>{@link de.cuioss.jwt.quarkus.interceptor.BearerTokenContextHolder} - Request-scoped context holder</li>
 * </ul>
 * <p>
 * The interceptor pattern complements the existing producer pattern, providing
 * developers with a choice between explicit validation (producer) and declarative
 * validation (interceptor).
 *
 * @author Oliver Wolff
 * @since 1.0
 */
package de.cuioss.jwt.quarkus.interceptor;
