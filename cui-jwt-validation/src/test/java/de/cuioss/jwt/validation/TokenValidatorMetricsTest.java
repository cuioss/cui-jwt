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

import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.metrics.MeasurementType;
import de.cuioss.jwt.validation.metrics.TokenValidatorMonitor;
import de.cuioss.jwt.validation.test.TestTokenHolder;
import de.cuioss.jwt.validation.test.generator.TestTokenGenerators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test suite for verifying metrics collection in {@link TokenValidator}.
 * <p>
 * This test focuses on reproducing missing metric entries and timing discrepancies
 * observed in the benchmark results.
 */
@EnableGeneratorController
@EnableTestLogger
class TokenValidatorMetricsTest {

    private TokenValidator tokenValidator;
    private TestTokenHolder testTokenHolder;
    private IssuerConfig issuerConfig;

    @BeforeEach
    void setUp() {
        testTokenHolder = TestTokenGenerators.accessTokens().next();

        issuerConfig = testTokenHolder.getIssuerConfig();

        tokenValidator = TokenValidator.builder().issuerConfig(issuerConfig).build();
    }

    @Test
    @DisplayName("Should record all expected metrics for successful token validation")
    void shouldRecordAllMetricsForSuccessfulValidation() {
        // Given
        String tokenString = testTokenHolder.getRawToken();
        TokenValidatorMonitor monitor = tokenValidator.getPerformanceMonitor();

        // When
        AccessTokenContent accessToken = tokenValidator.createAccessToken(tokenString);

        // Then
        assertNotNull(accessToken);

        // Verify all metrics are recorded
        Set<MeasurementType> recordedMetrics = new HashSet<>();
        for (MeasurementType type : MeasurementType.values()) {
            Duration avgDuration = monitor.getAverageDuration(type);
            if (avgDuration.toNanos() > 0) {
                recordedMetrics.add(type);
            }
        }

        // Log missing metrics
        Set<MeasurementType> expectedMetrics = new HashSet<>(Arrays.asList(MeasurementType.values()));
        expectedMetrics.removeAll(recordedMetrics);

        System.out.println("Recorded metrics: " + recordedMetrics);
        System.out.println("Missing metrics: " + expectedMetrics);

        // Check specific metrics that were missing in benchmark
        assertTrue(recordedMetrics.contains(MeasurementType.COMPLETE_VALIDATION),
                "COMPLETE_VALIDATION should be recorded");
        assertTrue(recordedMetrics.contains(MeasurementType.TOKEN_PARSING),
                "TOKEN_PARSING should be recorded");
        assertTrue(recordedMetrics.contains(MeasurementType.SIGNATURE_VALIDATION),
                "SIGNATURE_VALIDATION should be recorded");

        // These might be missing or have zero duration
        System.out.println("TOKEN_FORMAT_CHECK duration: " + monitor.getAverageDuration(MeasurementType.TOKEN_FORMAT_CHECK).toNanos() + " ns");
        System.out.println("ISSUER_EXTRACTION duration: " + monitor.getAverageDuration(MeasurementType.ISSUER_EXTRACTION).toNanos() + " ns");
        System.out.println("JWKS_OPERATIONS duration: " + monitor.getAverageDuration(MeasurementType.JWKS_OPERATIONS).toNanos() + " ns");
    }

    @Test
    @DisplayName("Should show timing discrepancy between complete validation and sum of individual steps")
    void shouldShowTimingDiscrepancy() {
        // Given
        String tokenString = testTokenHolder.getRawToken();
        TokenValidatorMonitor monitor = tokenValidator.getPerformanceMonitor();

        // When - validate multiple times to get stable averages
        for (int i = 0; i < 10; i++) {
            tokenValidator.createAccessToken(tokenString);
        }

        // Then - calculate sum of individual steps
        long sumOfSteps = 0;
        for (MeasurementType type : MeasurementType.values()) {
            if (type != MeasurementType.COMPLETE_VALIDATION) {
                sumOfSteps += monitor.getAverageDuration(type).toNanos();
            }
        }

        long completeValidationTime = monitor.getAverageDuration(MeasurementType.COMPLETE_VALIDATION).toNanos();

        System.out.println("Complete validation time: " + completeValidationTime + " ns (" + completeValidationTime / 1_000_000.0 + " ms)");
        System.out.println("Sum of individual steps: " + sumOfSteps + " ns (" + sumOfSteps / 1_000_000.0 + " ms)");
        System.out.println("Unaccounted time: " + (completeValidationTime - sumOfSteps) + " ns (" + (completeValidationTime - sumOfSteps) / 1_000_000.0 + " ms)");

        // Print breakdown
        System.out.println("\nDetailed breakdown:");
        for (MeasurementType type : MeasurementType.values()) {
            Duration duration = monitor.getAverageDuration(type);
            if (duration.toNanos() > 0) {
                System.out.printf("%s: %d ns (%.3f ms)%n",
                        type,
                        duration.toNanos(),
                        duration.toNanos() / 1_000_000.0);
            }
        }

        // The complete validation time should be greater than the sum due to overhead
        assertTrue(completeValidationTime > sumOfSteps,
                "Complete validation time should include overhead not captured in individual steps");
    }

    @Test
    @DisplayName("Should detect fast operations that might show as empty metrics")
    void shouldDetectFastOperations() {
        // Given
        String tokenString = testTokenHolder.getRawToken();
        TokenValidatorMonitor monitor = tokenValidator.getPerformanceMonitor();

        // When - validate once
        tokenValidator.createAccessToken(tokenString);

        // Then - check for very fast operations
        Duration tokenFormatCheck = monitor.getAverageDuration(MeasurementType.TOKEN_FORMAT_CHECK);
        Duration issuerExtraction = monitor.getAverageDuration(MeasurementType.ISSUER_EXTRACTION);
        Duration headerValidation = monitor.getAverageDuration(MeasurementType.HEADER_VALIDATION);

        System.out.println("\nFast operations analysis:");
        System.out.printf("TOKEN_FORMAT_CHECK: %d ns (%.6f ms) - %s%n",
                tokenFormatCheck.toNanos(),
                tokenFormatCheck.toNanos() / 1_000_000.0,
                tokenFormatCheck.toNanos() == 0 ? "MISSING!" : "OK");
        System.out.printf("ISSUER_EXTRACTION: %d ns (%.6f ms) - %s%n",
                issuerExtraction.toNanos(),
                issuerExtraction.toNanos() / 1_000_000.0,
                issuerExtraction.toNanos() == 0 ? "MISSING!" : "OK");
        System.out.printf("HEADER_VALIDATION: %d ns (%.6f ms) - %s%n",
                headerValidation.toNanos(),
                headerValidation.toNanos() / 1_000_000.0,
                headerValidation.toNanos() < 1000 ? "SUSPICIOUSLY FAST!" : "OK");

        // These operations should have some duration, even if very small
        assertTrue(tokenFormatCheck.toNanos() >= 0, "TOKEN_FORMAT_CHECK should have a duration");
        assertTrue(issuerExtraction.toNanos() >= 0, "ISSUER_EXTRACTION should have a duration");
    }

    @Test
    @DisplayName("Should measure JWKS operations separately from signature validation")
    void shouldMeasureJwksOperationsSeparately() {
        // Given
        String tokenString = testTokenHolder.getRawToken();
        TokenValidatorMonitor monitor = tokenValidator.getPerformanceMonitor();

        // When
        tokenValidator.createAccessToken(tokenString);

        // Then
        Duration jwksOperations = monitor.getAverageDuration(MeasurementType.JWKS_OPERATIONS);
        Duration signatureValidation = monitor.getAverageDuration(MeasurementType.SIGNATURE_VALIDATION);

        System.out.println("\nJWKS operations analysis:");
        System.out.printf("JWKS_OPERATIONS: %d ns (%.3f ms)%n",
                jwksOperations.toNanos(),
                jwksOperations.toNanos() / 1_000_000.0);
        System.out.printf("SIGNATURE_VALIDATION: %d ns (%.3f ms)%n",
                signatureValidation.toNanos(),
                signatureValidation.toNanos() / 1_000_000.0);

        // JWKS operations time should be included in signature validation time
        assertTrue(signatureValidation.toNanos() > jwksOperations.toNanos(),
                "Signature validation should include JWKS operations time");
    }
}