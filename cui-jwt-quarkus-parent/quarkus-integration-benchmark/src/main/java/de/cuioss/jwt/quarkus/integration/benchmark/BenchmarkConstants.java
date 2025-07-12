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
package de.cuioss.jwt.quarkus.integration.benchmark;

import lombok.experimental.UtilityClass;

/**
 * Shared constants for integration benchmark classes.
 * Centralizes string literals used across multiple benchmark classes.
 */
@UtilityClass
@SuppressWarnings("java:S1075") // ok for benchmark testing
public class BenchmarkConstants {

    // REST endpoint paths
    public static final String JWT_VALIDATE_PATH = "/jwt/validate";
    public static final String JWT_VALIDATE_ID_TOKEN_PATH = "/jwt/validate/id-token";
    public static final String JWT_VALIDATE_REFRESH_TOKEN_PATH = "/jwt/validate/refresh-token";
    public static final String HEALTH_CHECK_PATH = "/q/health/live";

    // HTTP headers
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
}