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
 * Jakarta JSON API bridge implementation using DSL-JSON for secure parsing.
 * <p>
 * This package provides adapter classes that bridge DSL-JSON parsing results
 * to Jakarta JSON API interfaces, enabling type-safe JSON handling while
 * maintaining the security benefits of DSL-JSON's configurable parsing limits.
 * <p>
 * Key components:
 * <ul>
 *   <li>{@link de.cuioss.tools.net.http.json.DslJsonObjectAdapter} - JsonObject implementation</li>
 *   <li>{@link de.cuioss.tools.net.http.json.DslJsonArrayAdapter} - JsonArray implementation</li>
 *   <li>{@link de.cuioss.tools.net.http.json.DslJsonValueFactory} - Factory for creating JsonValue instances</li>
 *   <li>{@link de.cuioss.tools.net.http.json.DslJsonStringAdapter} - JsonString implementation</li>
 *   <li>{@link de.cuioss.tools.net.http.json.DslJsonNumberAdapter} - JsonNumber implementation</li>
 * </ul>
 * <p>
 * The bridge implementation provides backward compatibility with existing code
 * that uses Jakarta JSON API while replacing the vulnerable Eclipse Parsson
 * with security-focused DSL-JSON parsing.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
package de.cuioss.tools.net.http.json;