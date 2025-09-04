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
package de.cuioss.jwt.quarkus.config;

import de.cuioss.jwt.quarkus.logging.CustomAccessLogFilter;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Configuration data for custom HTTP access logging filter.
 * This filter provides more granular control than Quarkus built-in access logging,
 * allowing filtering by HTTP status codes and specific paths.
 */
@Value
@Builder
public class AccessLogFilterConfig {

    /**
     * Minimum HTTP status code to log.
     * Only responses with status codes >= this value will be logged.
     * Common values:
     * - 200: Log all responses (equivalent to standard access log)
     * - 400: Log only client and server errors
     * - 500: Log only server errors
     */
    @Builder.Default
    int minStatusCode = 400;

    /**
     * Maximum HTTP status code to log.
     * Only responses with status codes less than or equal to this value will be logged.
     * Set to 599 to include all error codes.
     */
    @Builder.Default
    int maxStatusCode = 599;

    /**
     * Specific HTTP status codes to always log, regardless of min/max range.
     * Useful for logging specific success codes (like 201, 202) along with errors.
     */
    List<Integer> includeStatusCodes;

    /**
     * URL path patterns to include in logging.
     * If specified, only requests matching these patterns will be considered for logging.
     * Uses simple glob patterns (* and **).
     * Empty list means all paths are eligible.
     */
    List<String> includePaths;

    /**
     * URL path patterns to exclude from logging.
     * These patterns override include patterns.
     * Uses simple glob patterns (* and **).
     * Common exclusions: /health/*, /metrics/*, /jwt/validate
     */
    List<String> excludePaths;

    /**
     * Log format pattern.
     * Supports placeholders:
     * - {method}: HTTP method (GET, POST, etc.)
     * - {path}: Request path
     * - {status}: HTTP status code
     * - {duration}: Request duration in milliseconds
     * - {remoteAddr}: Remote IP address
     * - {userAgent}: User-Agent header
     */
    @Builder.Default
    String pattern = "{remoteAddr} {method} {path} -> {status} ({duration}ms)";

    /**
     * Logger name for access log entries.
     * Allows configuring separate log level and appenders for access logs.
     */
    @Builder.Default
    String loggerName = CustomAccessLogFilter.class.getName();
}