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
package de.cuioss.jwt.validation.metrics;

import de.cuioss.tools.logging.CuiLogger;
import de.cuioss.tools.net.http.retry.RetryContext;
import de.cuioss.tools.net.http.retry.RetryMetrics;
import lombok.NonNull;

import java.time.Duration;

import static de.cuioss.jwt.validation.JWTValidationLogMessages.INFO;
import static de.cuioss.jwt.validation.JWTValidationLogMessages.WARN;

/**
 * JWT-specific implementation of {@link RetryMetrics} that integrates with the
 * existing {@link TokenValidatorMonitor} metrics infrastructure.
 * 
 * This implementation records retry metrics using the established JWT validation
 * measurement types and provides detailed observability into retry behavior for
 * HTTP operations related to JWT validation (primarily JWKS loading).
 * 
 * <p><strong>Recorded Metrics:</strong></p>
 * <ul>
 *   <li>{@link MeasurementType#RETRY_ATTEMPT} - Individual attempt durations</li>
 *   <li>{@link MeasurementType#RETRY_COMPLETE} - Complete retry operation durations</li>
 *   <li>{@link MeasurementType#RETRY_DELAY} - Actual delay times between attempts</li>
 * </ul>
 * 
 * <p><strong>Thread Safety:</strong></p>
 * This implementation is thread-safe as it delegates to the thread-safe
 * {@link TokenValidatorMonitor}.
 * 
 * @author Oliver Wolff
 * @since 1.0
 */
public class JwtRetryMetrics implements RetryMetrics {

    private static final CuiLogger LOGGER = new CuiLogger(JwtRetryMetrics.class);

    private final TokenValidatorMonitor monitor;

    /**
     * Creates a new JWT retry metrics recorder with the specified monitor.
     * 
     * @param monitor the token validator monitor to record metrics to (must not be null)
     */
    public JwtRetryMetrics(@NonNull TokenValidatorMonitor monitor) {
        this.monitor = monitor;
    }

    @Override
    public void recordRetryStart(RetryContext context) {
        LOGGER.trace("Starting retry operation for '%s'", context.operationName());
        // No specific metric to record for retry start, just logging
    }

    @Override
    public void recordRetryComplete(RetryContext context, Duration totalDuration, boolean successful, int totalAttempts) {
        if (monitor.isEnabled(MeasurementType.RETRY_COMPLETE)) {
            monitor.recordMeasurement(MeasurementType.RETRY_COMPLETE, totalDuration.toNanos());
        }

        if (successful) {
            LOGGER.info(INFO.RETRY_OPERATION_COMPLETED.format(context.operationName(), totalAttempts, totalDuration.toMillis()));
        } else {
            LOGGER.warn(WARN.RETRY_OPERATION_FAILED.format(context.operationName(), totalAttempts, totalDuration.toMillis()));
        }
    }

    @Override
    public void recordRetryAttempt(RetryContext context, int attemptNumber, Duration attemptDuration,
            boolean successful) {
        if (monitor.isEnabled(MeasurementType.RETRY_ATTEMPT)) {
            monitor.recordMeasurement(MeasurementType.RETRY_ATTEMPT, attemptDuration.toNanos());
        }

        if (successful) {
            LOGGER.debug("Retry attempt {} succeeded for operation '{}' in {}ms", attemptNumber, context.operationName(), attemptDuration.toMillis());
        } else {
            LOGGER.debug("Retry attempt {} failed for operation '{}' after {}ms",
                    attemptNumber, context.operationName(), attemptDuration.toMillis());
        }
    }

    @Override
    public void recordRetryDelay(RetryContext context, int attemptNumber, Duration plannedDelay, Duration actualDelay) {
        if (monitor.isEnabled(MeasurementType.RETRY_DELAY)) {
            monitor.recordMeasurement(MeasurementType.RETRY_DELAY, actualDelay.toNanos());
        }

        LOGGER.trace("Retry delay for '%s' before attempt %s: planned=%sms, actual=%sms",
                context.operationName(), attemptNumber, plannedDelay.toMillis(), actualDelay.toMillis());

        // Log if there was a significant difference between planned and actual delay
        long delayDifference = Math.abs(actualDelay.toMillis() - plannedDelay.toMillis());
        if (delayDifference > 50) { // More than 50ms difference
            LOGGER.debug("Retry delay deviation for '{}': planned={}ms, actual={}ms, difference={}ms",
                    context.operationName(), plannedDelay.toMillis(), actualDelay.toMillis(), delayDifference);
        }
    }
}