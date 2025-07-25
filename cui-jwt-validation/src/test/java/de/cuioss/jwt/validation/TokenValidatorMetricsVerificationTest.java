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
package de.cuioss.jwt.validation;

import de.cuioss.jwt.validation.metrics.MeasurementType;
import de.cuioss.jwt.validation.metrics.TokenValidatorMonitor;
import de.cuioss.jwt.validation.test.TestTokenHolder;
import de.cuioss.jwt.validation.test.generator.TestTokenGenerators;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies metrics collection behavior and reproduces missing metrics issues.
 */
class TokenValidatorMetricsVerificationTest {

    private TokenValidator tokenValidator;
    private TestTokenHolder testTokenHolder;

    @BeforeEach
    void setUp() {
        testTokenHolder = TestTokenGenerators.accessTokens().next();
        tokenValidator = new TokenValidator(testTokenHolder.getIssuerConfig());
    }

    @Test
    void verifyMetricsCollectionAndTimingGaps() {
        // Given
        String tokenString = testTokenHolder.getRawToken();
        TokenValidatorMonitor monitor = tokenValidator.getPerformanceMonitor();

        // When - validate multiple times to get meaningful averages
        int iterations = 100;
        for (int i = 0; i < iterations; i++) {
            tokenValidator.createAccessToken(tokenString);
        }

        // Then - analyze metrics
        System.out.println("\n=== METRICS ANALYSIS ===");
        System.out.println("After " + iterations + " iterations:");

        // Check each metric type
        long totalIndividualSteps = 0;
        for (MeasurementType type : MeasurementType.values()) {
            Duration duration = monitor.getAverageDuration(type);
            long nanos = duration.toNanos();
            double millis = nanos / 1_000_000.0;

            if (type != MeasurementType.COMPLETE_VALIDATION && nanos > 0) {
                totalIndividualSteps += nanos;
            }

            String status = "OK";
            if (nanos == 0) {
                status = "MISSING!";
            } else if (nanos < 1000) { // Less than 1 microsecond
                status = "VERY FAST";
            }

            System.out.printf("%-30s: %10d ns (%8.3f ms) - %s%n",
                    type, nanos, millis, status);
        }

        // Calculate timing gap
        long completeValidationNanos = monitor.getAverageDuration(MeasurementType.COMPLETE_VALIDATION).toNanos();
        long timingGap = completeValidationNanos - totalIndividualSteps;
        double timingGapMillis = timingGap / 1_000_000.0;
        double timingGapPercentage = (timingGap * 100.0) / completeValidationNanos;

        System.out.println("\n=== TIMING GAP ANALYSIS ===");
        System.out.printf("Complete validation:     %10d ns (%8.3f ms)%n",
                completeValidationNanos, completeValidationNanos / 1_000_000.0);
        System.out.printf("Sum of individual steps: %10d ns (%8.3f ms)%n",
                totalIndividualSteps, totalIndividualSteps / 1_000_000.0);
        System.out.printf("Timing gap:              %10d ns (%8.3f ms) = %.1f%%%n",
                timingGap, timingGapMillis, timingGapPercentage);

        // Identify missing metrics (those that appear as empty in benchmark JSON)
        System.out.println("\n=== MISSING METRICS (0 duration) ===");
        for (MeasurementType type : MeasurementType.values()) {
            if (monitor.getAverageDuration(type).toNanos() == 0) {
                System.out.println("- " + type);
            }
        }

        // Verify our findings
        assertTrue(completeValidationNanos > 0, "Complete validation should have duration");
        assertTrue(timingGap > 0, "There should be overhead not captured in individual steps");

        // Check specific metrics that were missing in the benchmark
        System.out.println("\n=== BENCHMARK PROBLEMATIC METRICS ===");
        checkMetric("TOKEN_FORMAT_CHECK", monitor.getAverageDuration(MeasurementType.TOKEN_FORMAT_CHECK));
        checkMetric("ISSUER_EXTRACTION", monitor.getAverageDuration(MeasurementType.ISSUER_EXTRACTION));
        checkMetric("JWKS_OPERATIONS", monitor.getAverageDuration(MeasurementType.JWKS_OPERATIONS));
        checkMetric("HEADER_VALIDATION", monitor.getAverageDuration(MeasurementType.HEADER_VALIDATION));
    }

    private void checkMetric(String name, Duration duration) {
        long nanos = duration.toNanos();
        if (nanos == 0) {
            System.out.printf("%s: MISSING (0 ns) - would appear as empty in JSON%n", name);
        } else if (nanos < 1000) {
            System.out.printf("%s: %d ns (%.6f ms) - very fast, might round to 0%n",
                    name, nanos, nanos / 1_000_000.0);
        } else {
            System.out.printf("%s: %d ns (%.3f ms) - OK%n",
                    name, nanos, nanos / 1_000_000.0);
        }
    }
}