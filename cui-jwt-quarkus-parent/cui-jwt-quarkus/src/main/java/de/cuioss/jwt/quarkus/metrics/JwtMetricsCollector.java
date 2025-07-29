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
import de.cuioss.jwt.quarkus.producer.BearerTokenProducer;
import de.cuioss.jwt.validation.TokenValidator;
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
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.NonNull;

import java.util.Map;
import java.util.Optional;
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
    private static final String HTTP_REQUEST_DURATION = "cui.jwt.http.request.duration";
    private static final String HTTP_REQUEST_COUNT = "cui.jwt.http.request.count";

    private static final String TAG_EVENT_TYPE = "event_type";
    private static final String TAG_RESULT = "result";
    private static final String TAG_CATEGORY = "category";
    private static final String TAG_STEP = "step";
    private static final String TAG_STATUS = "status";
    private static final String TAG_TYPE = "type";

    private static final String RESULT_FAILURE = "failure";

    private final MeterRegistry registry;
    private final TokenValidator tokenValidator;
    private final BearerTokenProducer bearerTokenProducer;

    private SecurityEventCounter securityEventCounter;
    private TokenValidatorMonitor tokenValidatorMonitor;
    private HttpMetricsMonitor httpMetricsMonitor;


    // Caching of counters to avoid lookups
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    // Caching of timers to avoid lookups
    private final Map<String, Timer> timers = new ConcurrentHashMap<>();

    // Track last known counts to calculate deltas
    private final Map<SecurityEventCounter.EventType, Long> lastKnownCounts = new ConcurrentHashMap<>();
    private final Map<HttpMetricsMonitor.HttpRequestStatus, Long> lastKnownHttpStatusCounts = new ConcurrentHashMap<>();

    /**
     * Creates a new JwtMetricsCollector with the given MeterRegistry, TokenValidator, and BearerTokenProducer.
     *
     * @param registry the Micrometer registry
     * @param tokenValidator the token validator containing monitoring components
     * @param bearerTokenProducer the bearer token producer containing HTTP monitoring components
     */
    @Inject
    public JwtMetricsCollector(@NonNull MeterRegistry registry,
            @NonNull TokenValidator tokenValidator,
            @NonNull BearerTokenProducer bearerTokenProducer) {
        this.registry = registry;
        this.tokenValidator = tokenValidator;
        this.bearerTokenProducer = bearerTokenProducer;
    }

    /**
     * Initializes all metrics after dependency injection is complete.
     * This method is guaranteed to run before any business method can be called.
     */
    @PostConstruct
    void initialize() {
        LOGGER.info(INFO.INITIALIZING_JWT_METRICS_COLLECTOR::format);
        securityEventCounter = tokenValidator.getSecurityEventCounter();
        tokenValidatorMonitor = tokenValidator.getPerformanceMonitor();
        httpMetricsMonitor = bearerTokenProducer.getHttpMetricsMonitor();

        // Register counters for all event types
        registerEventCounters();

        // Register performance timers for all measurement types
        registerPerformanceTimers();

        // Register HTTP metrics
        registerHttpMetrics();

        // Initialize the last known counts
        Map<SecurityEventCounter.EventType, Long> currentCounts = securityEventCounter.getCounters();
        lastKnownCounts.putAll(currentCounts);

        // Initialize HTTP status counts
        if (httpMetricsMonitor != null) {
            Map<HttpMetricsMonitor.HttpRequestStatus, Long> currentHttpStatusCounts = httpMetricsMonitor.getRequestStatusCounts();
            lastKnownHttpStatusCounts.putAll(currentHttpStatusCounts);
        }

        LOGGER.info(INFO.JWT_METRICS_COLLECTOR_INITIALIZED.format(counters.size() + timers.size()));

        // Force initial update to ensure metrics are visible immediately
        updateCounters();
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
     * Registers timers for enabled performance measurement types only.
     */
    private void registerPerformanceTimers() {
        if (tokenValidatorMonitor == null) {
            LOGGER.warn(WARN.TOKEN_VALIDATOR_MONITOR_NOT_AVAILABLE::format);
            return;
        }

        // Only register timers for measurement types that are enabled in the monitor
        for (MeasurementType measurementType : tokenValidatorMonitor.getEnabledTypes()) {
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

            LOGGER.debug("Registered timer for enabled measurement type %s", measurementType.name());
        }

        LOGGER.debug("Registered %s performance timers for enabled measurement types", timers.size());
    }

    /**
     * Registers HTTP-level metrics for request processing.
     */
    private void registerHttpMetrics() {
        if (httpMetricsMonitor == null) {
            LOGGER.warn(WARN.HTTP_METRICS_MONITOR_NOT_AVAILABLE::format);
            return;
        }

        // Register timers for HTTP measurement types
        for (HttpMetricsMonitor.HttpMeasurementType measurementType : HttpMetricsMonitor.HttpMeasurementType.values()) {
            Tags tags = Tags.of(Tag.of(TAG_TYPE, measurementType.name().toLowerCase()));

            Timer timer = Timer.builder(HTTP_REQUEST_DURATION)
                    .tags(tags)
                    .description("Duration of HTTP-level JWT processing: " + measurementType.getDescription())
                    .register(registry);

            timers.put("HTTP_" + measurementType.name(), timer);
            LOGGER.debug("Registered HTTP timer for measurement type %s", measurementType.name());
        }

        // Register counters for HTTP request statuses
        for (HttpMetricsMonitor.HttpRequestStatus status : HttpMetricsMonitor.HttpRequestStatus.values()) {
            Tags tags = Tags.of(Tag.of(TAG_STATUS, status.name().toLowerCase()));

            Counter counter = Counter.builder(HTTP_REQUEST_COUNT)
                    .tags(tags)
                    .description("Count of HTTP requests by status")
                    .baseUnit("requests")
                    .register(registry);

            counters.put("HTTP_STATUS_" + status.name(), counter);
            LOGGER.debug("Registered HTTP counter for status %s", status.name());
        }
    }

    /**
     * Updates all counters and performance metrics from the current state.
     * This method is called periodically to ensure metrics are up to date.
     * <p>
     * The interval can be configured via the property: cui.jwt.metrics.collection.interval
     * Default: 10s (production), can be set to 2s for faster integration testing.
     */
    @Scheduled(every = "${" + JwtPropertyKeys.METRICS.COLLECTION_INTERVAL + ":10s}")
    public void updateCounters() {
        updateSecurityEventCounters();
        updatePerformanceMetrics();
        updateHttpMetrics();
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
     * Only processes measurement types that are enabled in the monitor configuration.
     */
    private void updatePerformanceMetrics() {
        if (tokenValidatorMonitor == null) {
            return;
        }

        // Only process measurement types that are enabled and have registered timers
        for (MeasurementType measurementType : tokenValidatorMonitor.getEnabledTypes()) {
            Timer timer = timers.get(measurementType.name());
            if (timer != null) {
                // Get the current metrics from the monitor
                var metricsOpt = tokenValidatorMonitor.getValidationMetrics(measurementType);

                // Record the duration in the timer (we use a sample with the P50 duration)
                // Note: This is a simplified approach - in practice, we might want to record individual measurements
                if (metricsOpt.isPresent()) {
                    var averageDuration = metricsOpt.get().p50();
                    if (!averageDuration.isZero()) {
                        timer.record(averageDuration);
                        LOGGER.debug("Updated timer for measurement type %s with average %s",
                                measurementType.name(), averageDuration);
                    }
                }
            }
        }
    }

    /**
     * Updates HTTP-level metrics from the HttpMetricsMonitor state.
     */
    private void updateHttpMetrics() {
        if (httpMetricsMonitor == null) {
            return;
        }

        // Update HTTP performance timers
        for (HttpMetricsMonitor.HttpMeasurementType measurementType : HttpMetricsMonitor.HttpMeasurementType.values()) {
            Timer timer = timers.get("HTTP_" + measurementType.name());
            if (timer != null) {
                var averageDuration = httpMetricsMonitor.getAverageDuration(measurementType);
                if (!averageDuration.isZero()) {
                    timer.record(averageDuration);
                    LOGGER.debug("Updated HTTP timer for measurement type %s with average %s",
                            measurementType.name(), averageDuration);
                }
            }
        }

        // Update HTTP request status counters
        Map<HttpMetricsMonitor.HttpRequestStatus, Long> statusCounts = httpMetricsMonitor.getRequestStatusCounts();
        for (Map.Entry<HttpMetricsMonitor.HttpRequestStatus, Long> entry : statusCounts.entrySet()) {
            HttpMetricsMonitor.HttpRequestStatus status = entry.getKey();
            Long currentCount = entry.getValue();

            // Get the last known count for this status
            Long lastCount = lastKnownHttpStatusCounts.getOrDefault(status, 0L);

            // Calculate the delta
            long delta = currentCount - lastCount;

            // Only update if there's a change
            if (delta > 0) {
                Counter counter = counters.get("HTTP_STATUS_" + status.name());
                if (counter != null) {
                    counter.increment(delta);
                    LOGGER.debug("Updated HTTP status counter for %s by %d", status.name(), delta);
                }

                // Update the last known count
                lastKnownHttpStatusCounts.put(status, currentCount);
            }
        }
    }
}
