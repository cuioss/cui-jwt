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
package de.cuioss.jwt.quarkus.metrics;

import de.cuioss.jwt.quarkus.config.JwtPropertyKeys;
import de.cuioss.jwt.validation.security.EventCategory;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.tools.logging.CuiLogger;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.quarkus.arc.Unremovable;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import static de.cuioss.jwt.quarkus.CuiJwtQuarkusLogMessages.INFO;
import static de.cuioss.jwt.quarkus.CuiJwtQuarkusLogMessages.WARN;

/**
 * Collects security event metrics from the {@link SecurityEventCounter} and
 * exposes them as Micrometer metrics.
 * <p>
 * This collector registers counters for each security event type and automatically
 * updates them when events are triggered. It only needs access to the
 * SecurityEventCounter to perform its job.
 * <p>
 * All metrics follow Micrometer naming conventions and include appropriate tags
 * for filtering:
 * <ul>
 *   <li>cui.jwt.validation.errors - Counter for validation errors by type</li>
 * </ul>
 * <p>
 * Each metric includes relevant tags:
 * <ul>
 *   <li>event_type - The type of security event</li>
 *   <li>result - The validation result (failure)</li>
 *   <li>category - The category of event (structure, signature, semantic)</li>
 * </ul>
 */
@ApplicationScoped
@Unremovable
public class JwtMetricsCollector {

    private static final CuiLogger LOGGER = new CuiLogger(JwtMetricsCollector.class);

    private static final String VALIDATION_ERRORS = JwtPropertyKeys.METRICS.VALIDATION_ERRORS;

    private static final String TAG_EVENT_TYPE = "event_type";
    private static final String TAG_RESULT = "result";
    private static final String TAG_CATEGORY = "category";

    private static final String RESULT_FAILURE = "failure";

    private final MeterRegistry registry;
    private final SecurityEventCounter securityEventCounter;

    // Caching of counters to avoid lookups
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    // Track last known counts to calculate deltas
    private final Map<SecurityEventCounter.EventType, Long> lastKnownCounts = new ConcurrentHashMap<>();

    /**
     * Creates a new JwtMetricsCollector with the given MeterRegistry and SecurityEventCounter.
     *
     * @param registry the Micrometer registry
     * @param securityEventCounter the security event counter
     */
    @Inject
    public JwtMetricsCollector(MeterRegistry registry, SecurityEventCounter securityEventCounter) {
        this.registry = registry;
        this.securityEventCounter = securityEventCounter;
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

        // Initialize the last known counts
        Map<SecurityEventCounter.EventType, Long> currentCounts = securityEventCounter.getCounters();
        lastKnownCounts.putAll(currentCounts);

        LOGGER.info(INFO.JWT_METRICS_COLLECTOR_INITIALIZED.format(counters.size()));
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
     * Updates all counters from the current SecurityEventCounter state.
     * This method is called periodically to ensure metrics are up to date.
     */
    @Scheduled(every = "10s")
    public void updateCounters() {
        if (securityEventCounter == null) {
            return;
        }

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
}
