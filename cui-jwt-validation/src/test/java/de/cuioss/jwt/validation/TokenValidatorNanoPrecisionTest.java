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

import de.cuioss.tools.logging.CuiLogger;
import de.cuioss.jwt.validation.metrics.MeasurementType;
import de.cuioss.jwt.validation.metrics.TokenValidatorMonitor;
import de.cuioss.jwt.validation.test.TestTokenHolder;
import de.cuioss.jwt.validation.test.generator.TestTokenGenerators;
import de.cuioss.tools.concurrent.StripedRingBufferStatistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

/**
 * Demonstrates nanosecond precision issues that cause metrics to appear as 0.
 */
class TokenValidatorNanoPrecisionTest {

    private static final CuiLogger log = new CuiLogger(TokenValidatorNanoPrecisionTest.class);

    private TokenValidator tokenValidator;
    private TestTokenHolder testTokenHolder;

    @BeforeEach
    void setUp() {
        testTokenHolder = TestTokenGenerators.accessTokens().next();
        tokenValidator = TokenValidator.builder().issuerConfig(testTokenHolder.getIssuerConfig()).build();
    }

    @Test
    void demonstrateNanoPrecisionIssue() {
        // Given
        String tokenString = testTokenHolder.getRawToken();
        TokenValidatorMonitor monitor = tokenValidator.getPerformanceMonitor();

        // When - validate just once to see individual operation times
        tokenValidator.createAccessToken(tokenString);

        log.info("\n=== SINGLE VALIDATION RUN ===");
        printMetrics(monitor);

        // When - validate many times to see averaging effect
        for (int i = 0; i < 999; i++) {
            tokenValidator.createAccessToken(tokenString);
        }

        log.info("\n=== AFTER 1000 RUNS (AVERAGED) ===");
        printMetrics(monitor);

        // Demonstrate the issue
        log.info("\n=== NANOSECOND PRECISION ISSUE ===");

        // These operations are so fast they often measure < 1000 ns
        Duration tokenFormatCheck = monitor.getValidationMetrics(MeasurementType.TOKEN_FORMAT_CHECK)
                .map(StripedRingBufferStatistics::p50).orElse(Duration.ZERO);
        Duration issuerExtraction = monitor.getValidationMetrics(MeasurementType.ISSUER_EXTRACTION)
                .map(StripedRingBufferStatistics::p50).orElse(Duration.ZERO);

        log.info("Fast operations that might round to 0:");
        log.info("TOKEN_FORMAT_CHECK: %d ns - String.isBlank() check%n", tokenFormatCheck.toNanos());
        log.info("ISSUER_EXTRACTION: %d ns - Optional.get() from decoded JWT%n", issuerExtraction.toNanos());

        // The issue: when converted to milliseconds for JSON export, these become 0.000ms → 0
        log.info("\nWhen converted to milliseconds for JSON:");
        log.info("TOKEN_FORMAT_CHECK: %.3f ms → appears as empty/0 in JSON%n",
                tokenFormatCheck.toNanos() / 1_000_000.0);
        log.info("ISSUER_EXTRACTION: %.3f ms → appears as empty/0 in JSON%n",
                issuerExtraction.toNanos() / 1_000_000.0);

        // JWKS operations are only measured during signature validation
        log.info("\nJWKS_OPERATIONS issue:");
        Duration jwksOps = monitor.getValidationMetrics(MeasurementType.JWKS_OPERATIONS)
                .map(StripedRingBufferStatistics::p50).orElse(Duration.ZERO);
        log.info("JWKS_OPERATIONS: %d ns%n", jwksOps.toNanos());
        log.info("This is 0 because JwksLoader creation is not the actual JWKS fetch.");
        log.info("The actual JWKS operations happen inside signature validation.");
    }

    private void printMetrics(TokenValidatorMonitor monitor) {
        for (MeasurementType type : MeasurementType.values()) {
            Duration duration = monitor.getValidationMetrics(type)
                    .map(StripedRingBufferStatistics::p50).orElse(Duration.ZERO);
            long nanos = duration.toNanos();
            if (nanos > 0) {
                log.info("%-30s: %10d ns (%.6f ms)%n",
                        type, nanos, nanos / 1_000_000.0);
            }
        }
    }
}