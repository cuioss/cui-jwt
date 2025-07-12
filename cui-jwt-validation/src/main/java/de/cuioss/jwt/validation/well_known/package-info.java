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
 * Provides classes for handling OpenID Connect Discovery, specifically the
 * retrieval and processing of OIDC Provider Configuration Information from
 * well-known endpoints.
 * <p>
 * The main components include:
 * <ul>
 *   <li>{@link de.cuioss.jwt.validation.well_known.WellKnownResolver} - Interface for well-known endpoint resolution</li>
 *   <li>{@link de.cuioss.jwt.validation.well_known.HttpWellKnownResolver} - HTTP-based implementation with health checking</li>
 *   <li>Support classes for HTTP operations, JSON parsing, and endpoint mapping</li>
 * </ul>
 * <p>
 * This package follows the same design patterns as the JWKS loader system, providing:
 * <ul>
 *   <li>Lazy loading with thread-safe initialization</li>
 *   <li>Health checking and status reporting</li>
 *   <li>Simple, direct endpoint loading without retry mechanisms</li>
 *   <li>Configurable timeouts and SSL settings</li>
 * </ul>
 */
package de.cuioss.jwt.validation.well_known;
