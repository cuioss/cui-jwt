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
package de.cuioss.jwt.quarkus.metrics;

import de.cuioss.jwt.quarkus.config.JwtPropertyKeys;
import de.cuioss.jwt.validation.metrics.MeasurementType;
import de.cuioss.jwt.validation.metrics.TokenValidatorMonitor;
import de.cuioss.jwt.validation.security.EventCategory;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.tools.logging.CuiLogger;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.quarkus.arc.Unremovable;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static de.cuioss.jwt.quarkus.CuiJwtQuarkusLogMessages.INFO;
import static de.cuioss.jwt.quarkus.CuiJwtQuarkusLogMessages.WARN;

/**
 * Collects JWT validation metrics from both {@link SecurityEventCounter} and
 * {@link TokenValidatorMonitor} and exposes them as Micrometer metrics.
 * <p>
 * This collector registers metrics for security events and performance measurements:
 * <ul>
 *   <li>Security Event Counters - from {@link SecurityEventCounter}</li>
 *   <li>Performance Timers - from {@link TokenValidatorMonitor}</li>
 * </ul>
 * <p>
 * All metrics follow Micrometer naming conventions and include appropriate tags
 * for filtering:
 * <ul>
 *   <li>cui.jwt.validation.errors - Counter for validation errors by type</li>
 *   <li>cui.jwt.validation.duration - Timer for validation pipeline steps</li>
 * </ul>
 * <p>
 * Security event metrics include tags:
 * <ul>
 *   <li>event_type - The type of security event</li>
 *   <li>result - The validation result (failure)</li>
 *   <li>category - The category of event (structure, signature, semantic)</li>
 * </ul>
 * <p>
 * Performance metrics include tags:
 * <ul>
 *   <li>step - The validation pipeline step (parsing, header, signature, claims, jwks, complete)</li>
 * </ul>
 */
@ApplicationScoped
@Unremovable
public class JwtMetricsCollector {

    private static final CuiLogger LOGGER = new CuiLogger(JwtMetricsCollector.class);

    private static final String VALIDATION_ERRORS = JwtPropertyKeys.METRICS.VALIDATION_ERRORS;
    private static final String VALIDATION_DURATION = JwtPropertyKeys.METRICS.VALIDATION_DURATION;

    private static final String TAG_EVENT_TYPE = "event_type";
    private static final String TAG_RESULT = "result";
    private static final String TAG_CATEGORY = "category";
    private static final String TAG_STEP = "step";

    private static final String RESULT_FAILURE = "failure";

    private final MeterRegistry registry;
    private final SecurityEventCounter securityEventCounter;
    private final TokenValidatorMonitor tokenValidatorMonitor;

    // Caching of counters to avoid lookups
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    // Caching of timers to avoid lookups
    private final Map<String, Timer> timers = new ConcurrentHashMap<>();

    // Track last known counts to calculate deltas
    private final Map<SecurityEventCounter.EventType, Long> lastKnownCounts = new ConcurrentHashMap<>();

    /**
     * Creates a new JwtMetricsCollector with the given MeterRegistry, SecurityEventCounter, and TokenValidatorMonitor.
     *
     * @param registry the Micrometer registry
     * @param securityEventCounter the security event counter
     * @param tokenValidatorMonitor the performance monitor
     */
    @Inject
    public JwtMetricsCollector(MeterRegistry registry, SecurityEventCounter securityEventCounter, TokenValidatorMonitor tokenValidatorMonitor) {
        this.registry = registry;
        this.securityEventCounter = securityEventCounter;
        this.tokenValidatorMonitor = tokenValidatorMonitor;
    }

    /**
     * Initializes metrics collection on application startup.
     *
     * @param event the startup event
     */
    void onStart(@Observes StartupEvent event) {
        LOGGER.info(INFO.INITIALIZING_JWT_METRICS_COLLECTOR::format);
        initializeMetrics();
    }

    /**
     * Initializes all metrics.
     */
    private void initializeMetrics() {
        if (securityEventCounter == null) {
            LOGGER.warn(WARN.SECURITY_EVENT_COUNTER_NOT_AVAILABLE::format);
            return;
        }

        // Register counters for all event types
        registerEventCounters();

        // Register performance timers for all measurement types
        registerPerformanceTimers();

        // Initialize the last known counts
        Map<SecurityEventCounter.EventType, Long> currentCounts = securityEventCounter.getCounters();
        lastKnownCounts.putAll(currentCounts);

        LOGGER.info(INFO.JWT_METRICS_COLLECTOR_INITIALIZED.format(counters.size() + timers.size()));
    }

    /**
     * Registers counters for all security event types.
     *
     */
    private void registerEventCounters() {
        // For each event type, create a counter with appropriate tags
        for (SecurityEventCounter.EventType eventType : SecurityEventCounter.EventType.values()) {
            // Create tags for this event type
            Tags tags = Tags.of(
                    Tag.of(TAG_EVENT_TYPE, eventType.name()),
                    Tag.of(TAG_RESULT, RESULT_FAILURE)
            );

            // Add category tag if available
            EventCategory category = eventType.getCategory();
            if (category != null) {
                tags = tags.and(Tag.of(TAG_CATEGORY, category.name()));
            }

            // Register the counter
            Counter counter = Counter.builder(VALIDATION_ERRORS)
                    .tags(tags)
                    .description("Number of JWT validation errors by type")
                    .baseUnit("errors")
                    .register(registry);

            // Store the counter for later updates
            counters.put(eventType.name(), counter);

            LOGGER.debug("Registered counter for event type %s", eventType.name());
        }
    }

    /**
     * Registers timers for all performance measurement types.
     */
    private void registerPerformanceTimers() {
        if (tokenValidatorMonitor == null) {
            LOGGER.warn(WARN.TOKEN_VALIDATOR_MONITOR_NOT_AVAILABLE::format);
            return;
        }

        // For each measurement type, create a timer with appropriate tags
        for (MeasurementType measurementType : MeasurementType.values()) {
            // Create tags for this measurement type
            Tags tags = Tags.of(
                    Tag.of(TAG_STEP, measurementType.name().toLowerCase())
            );

            // Register the timer
            Timer timer = Timer.builder(VALIDATION_DURATION)
                    .tags(tags)
                    .description("Duration of JWT validation pipeline steps: " + measurementType.getDescription())
                    .register(registry);

            // Store the timer for later updates
            timers.put(measurementType.name(), timer);

            LOGGER.debug("Registered timer for measurement type %s", measurementType.name());
        }
    }

    /**
     * Updates all counters and performance metrics from the current state.
     * This method is called periodically to ensure metrics are up to date.
     */
    @Scheduled(every = "10s")
    public void updateCounters() {
        if (securityEventCounter == null) {
            return;
        }

        updateSecurityEventCounters();
        updatePerformanceMetrics();
    }

    /**
     * Updates security event counters from the SecurityEventCounter state.
     */
    private void updateSecurityEventCounters() {

        // Get current counts for all event types
        Map<SecurityEventCounter.EventType, Long> currentCounts = securityEventCounter.getCounters();

        // Update counters based on current counts
        for (Map.Entry<SecurityEventCounter.EventType, Long> entry : currentCounts.entrySet()) {
            SecurityEventCounter.EventType eventType = entry.getKey();
            Long currentCount = entry.getValue();

            // Get the last known count for this event type
            Long lastCount = lastKnownCounts.getOrDefault(eventType, 0L);

            // Calculate the delta
            long delta = currentCount - lastCount;

            // Only update if there's a change
            if (delta > 0) {
                // Get the counter for this event type
                Counter counter = counters.get(eventType.name());
                if (counter != null) {
                    // Increment the counter by the delta
                    counter.increment(delta);
                    LOGGER.debug("Updated counter for event type %s by %d", eventType.name(), delta);
                }

                // Update the last known count
                lastKnownCounts.put(eventType, currentCount);
            }
        }
    }

    /**
     * Updates performance metrics from the TokenValidatorMonitor state.
     * Records the current average duration for each measurement type as gauge values.
     */
    private void updatePerformanceMetrics() {
        if (tokenValidatorMonitor == null) {
            return;
        }

        // For each measurement type, record the current average as a gauge
        for (MeasurementType measurementType : MeasurementType.values()) {
            Timer timer = timers.get(measurementType.name());
            if (timer != null) {
                // Get the current average duration from the monitor
                var averageDuration = tokenValidatorMonitor.getAverageDuration(measurementType);

                // Record the duration in the timer (we use a sample with the average duration)
                // Note: This is a simplified approach - in practice, we might want to record individual measurements
                if (!averageDuration.isZero()) {
                    timer.record(averageDuration);
                    LOGGER.debug("Updated timer for measurement type %s with average %s",
                            measurementType.name(), averageDuration);
                }
            }
        }
    }
}
