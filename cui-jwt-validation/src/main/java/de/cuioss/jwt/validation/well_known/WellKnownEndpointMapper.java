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
package de.cuioss.jwt.validation.well_known;

import de.cuioss.jwt.validation.JWTValidationLogMessages;
import de.cuioss.tools.logging.CuiLogger;
import de.cuioss.tools.net.http.HttpHandler;
import de.cuioss.tools.net.http.client.HttpHandlerProvider;
import de.cuioss.tools.net.http.client.ResilientHttpHandler;
import de.cuioss.tools.net.http.converter.StringContentConverter;
import de.cuioss.tools.net.http.retry.RetryStrategy;

import java.net.URL;
import java.util.Map;

/**
 * Handles endpoint mapping and URL validation for well-known discovery.
 * <p>
 * This class is responsible for:
 * <ul>
 *   <li>Creating ResilientHttpHandler instances for discovered endpoints</li>
 *   <li>Validating endpoint URLs and accessibility</li>
 *   <li>Managing endpoint mappings with caching and retry capabilities</li>
 *   <li>Tracking endpoint status for health monitoring</li>
 * </ul>
 * <p>
 * All created endpoints include ETag awareness, retry capabilities, and status tracking
 * for resilient and efficient operation.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
class WellKnownEndpointMapper {

    private static final CuiLogger LOGGER = new CuiLogger(WellKnownEndpointMapper.class);

    private final HttpHandlerProvider handlerProvider;

    WellKnownEndpointMapper(HttpHandlerProvider handlerProvider) {
        this.handlerProvider = handlerProvider;
    }

    /**
     * Adds a ResilientHttpHandler to the map of endpoints.
     *
     * @param map The map to add to
     * @param key The key for the ResilientHttpHandler
     * @param urlString The URL string to add
     * @param wellKnownUrl The well-known URL (used for error messages)
     * @param isRequired Whether this URL is required
     * @return true if successful (or optional and missing), false on error
     */
    boolean addHttpHandlerToMap(Map<String, ResilientHttpHandler<String>> map, String key, String urlString, URL wellKnownUrl, boolean isRequired) {
        if (urlString == null) {
            if (isRequired) {
                LOGGER.error(JWTValidationLogMessages.ERROR.REQUIRED_URL_FIELD_MISSING.format(key, wellKnownUrl));
                return false;
            }
            LOGGER.debug(JWTValidationLogMessages.DEBUG.OPTIONAL_URL_FIELD_MISSING.format(key, wellKnownUrl));
            return true;
        }
        try {
            // Create a new HttpHandler for the specific endpoint URL
            HttpHandler endpointHandler = handlerProvider.getHttpHandler().asBuilder()
                    .uri(urlString)
                    .build();

            // Create a new HttpHandlerProvider that provides this specific handler and the same retry strategy
            HttpHandlerProvider endpointProvider = new HttpHandlerProvider() {
                @Override
                public HttpHandler getHttpHandler() {
                    return endpointHandler;
                }

                @Override
                public RetryStrategy getRetryStrategy() {
                    return handlerProvider.getRetryStrategy();
                }
            };

            // Create ResilientHttpHandler with String content converter for generic endpoints
            ResilientHttpHandler<String> resilientHandler = new ResilientHttpHandler<>(
                    endpointProvider,
                    StringContentConverter.identity()
            );

            map.put(key, resilientHandler);
            LOGGER.debug("Created ResilientHttpHandler for endpoint %s: %s", key, urlString);
            return true;
        } catch (IllegalArgumentException e) {
            LOGGER.error(e, JWTValidationLogMessages.ERROR.MALFORMED_URL_FIELD.format(key, urlString, wellKnownUrl, e.getMessage()));
            return false;
        }
    }

    /**
     * Performs accessibility check for a specific endpoint.
     *
     * @param endpointName The name of the endpoint
     * @param handler The ResilientHttpHandler for the endpoint
     */
    void performAccessibilityCheck(String endpointName, ResilientHttpHandler<String> handler) {
        if (handler != null) {
            // Try to load from the endpoint to check accessibility and populate cache
            var result = handler.load();
            if (result.isValid()) {
                LOGGER.debug(JWTValidationLogMessages.DEBUG.ACCESSIBILITY_CHECK_SUCCESSFUL.format(endpointName, "endpoint", result.getState()));
            } else {
                LOGGER.warn(JWTValidationLogMessages.WARN.ACCESSIBILITY_CHECK_HTTP_ERROR.format(endpointName, "endpoint", result.getState()));
            }
        }
    }
}