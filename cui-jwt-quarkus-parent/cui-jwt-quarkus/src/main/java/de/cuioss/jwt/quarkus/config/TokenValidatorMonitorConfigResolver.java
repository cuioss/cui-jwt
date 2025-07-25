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

import de.cuioss.jwt.validation.metrics.MeasurementType;
import de.cuioss.jwt.validation.metrics.TokenValidatorMonitorConfig;
import de.cuioss.tools.logging.CuiLogger;
import lombok.NonNull;
import org.eclipse.microprofile.config.Config;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static de.cuioss.jwt.quarkus.CuiJwtQuarkusLogMessages.INFO;

/**
 * Resolver for creating {@link TokenValidatorMonitorConfig} instances from Quarkus configuration properties.
 * <p>
 * This class handles the configuration resolution for JWT validation metrics monitoring settings,
 * using the builder pattern and delegating validation to the underlying TokenValidatorMonitorConfig builder.
 * </p>
 * <p>
 * The resolver supports configurable monitoring to optimize performance by only recording
 * metrics for the measurement types that are actually needed. When monitoring is disabled
 * or no measurement types are configured, all monitoring operations become no-ops.
 * </p>
 * <p>
 * Configuration properties:
 * <ul>
 *   <li>{@code cui.jwt.metrics.monitor.enabled} - Whether metrics monitoring is enabled</li>
 *   <li>{@code cui.jwt.metrics.monitor.window-size} - Size of the rolling measurement window</li>
 *   <li>{@code cui.jwt.metrics.monitor.measurement-types} - Comma-separated list of measurement types to monitor</li>
 * </ul>
 *
 * @since 1.0
 */
public class TokenValidatorMonitorConfigResolver {

    private static final CuiLogger LOGGER = new CuiLogger(TokenValidatorMonitorConfigResolver.class);

    private final Config config;

    /**
     * Creates a new TokenValidatorMonitorConfigResolver with the specified configuration.
     *
     * @param config the Quarkus configuration to read from
     */
    public TokenValidatorMonitorConfigResolver(@NonNull Config config) {
        this.config = config;
    }

    /**
     * Resolves the TokenValidatorMonitorConfig from configuration properties.
     * <p>
     * Returns null if monitoring is disabled, which will cause the TokenValidator
     * to use the default monitoring configuration (all types enabled).
     * </p>
     *
     * @return the resolved monitor configuration, or null if monitoring is disabled
     */
    public TokenValidatorMonitorConfig resolveMonitorConfig() {
        LOGGER.debug("Resolving TokenValidatorMonitorConfig from configuration");

        // Check if monitoring is enabled
        boolean enabled = config.getOptionalValue(JwtPropertyKeys.METRICS.MONITOR.ENABLED, Boolean.class)
                .orElse(true);

        if (!enabled) {
            LOGGER.info(INFO.MONITOR_CONFIG_DISABLED::format);
            return TokenValidatorMonitorConfig.disabled();
        }

        // Resolve window size
        int windowSize = config.getOptionalValue(JwtPropertyKeys.METRICS.MONITOR.WINDOW_SIZE, Integer.class)
                .orElse(TokenValidatorMonitorConfig.DEFAULT_WINDOW_SIZE);

        // Resolve measurement types
        Set<MeasurementType> measurementTypes = resolveMeasurementTypes();

        TokenValidatorMonitorConfig.TokenValidatorMonitorConfigBuilder builder = TokenValidatorMonitorConfig.builder()
                .windowSize(windowSize);

        // Add each measurement type to the builder
        for (MeasurementType measurementType : measurementTypes) {
            builder.measurementType(measurementType);
        }

        TokenValidatorMonitorConfig result = builder.build();

        LOGGER.info(INFO.MONITOR_CONFIG_RESOLVED.format(
                result.getWindowSize(),
                result.getMeasurementTypes().size(),
                result.getMeasurementTypes().toString()
        ));

        return result;
    }

    /**
     * Resolves the set of measurement types from configuration.
     *
     * @return the set of measurement types to monitor
     */
    private Set<MeasurementType> resolveMeasurementTypes() {
        Optional<String> measurementTypesConfig = config.getOptionalValue(
                JwtPropertyKeys.METRICS.MONITOR.MEASUREMENT_TYPES, String.class);

        if (measurementTypesConfig.isEmpty()) {
            // Default: monitor all types
            LOGGER.debug("No measurement types configured, using all types");
            return TokenValidatorMonitorConfig.ALL_MEASUREMENT_TYPES;
        }

        String configValue = measurementTypesConfig.get().trim();
        if (configValue.isEmpty()) {
            // Empty configuration means no monitoring
            LOGGER.debug("Empty measurement types configuration, disabling monitoring");
            return EnumSet.noneOf(MeasurementType.class);
        }

        if ("ALL".equalsIgnoreCase(configValue)) {
            // Special value for all types
            LOGGER.debug("ALL measurement types configured");
            return TokenValidatorMonitorConfig.ALL_MEASUREMENT_TYPES;
        }

        // Parse comma-separated list
        List<MeasurementType> measurementTypes = Arrays.stream(configValue.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(this::parseMeasurementType)
                .toList();

        Set<MeasurementType> result = measurementTypes.isEmpty() ?
                EnumSet.noneOf(MeasurementType.class) :
                EnumSet.copyOf(measurementTypes);

        LOGGER.debug("Resolved measurement types: {}", result);
        return result;
    }

    /**
     * Parses a single measurement type string.
     *
     * @param typeString the string to parse
     * @return the parsed measurement type
     * @throws IllegalArgumentException if the string is not a valid measurement type
     */
    private MeasurementType parseMeasurementType(String typeString) {
        try {
            return MeasurementType.valueOf(typeString.toUpperCase());
        } catch (IllegalArgumentException e) {
            String validValues = Arrays.stream(MeasurementType.values())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(
                    "Invalid measurement type: '" + typeString + "'. Valid values are: " + validValues, e);
        }
    }
}