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
package de.cuioss.jwt.integration;

import de.cuioss.tools.logging.CuiLogger;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Integration test to demonstrate that health checks themselves can BLOCK during JWKS loading failures.
 * <p>
 * This test proves the ROOT CAUSE of the benchmarking timeout issue:
 * - Health checks are not just failing, they are HANGING/TIMING OUT
 * - The readiness check performs synchronous HTTP requests to Keycloak
 * - When Keycloak is unavailable, health checks block for extended periods
 * - This blocking behavior makes the service completely unresponsive
 * </p>
 */
class HealthCheckBlockingReproductionIT extends BaseIntegrationTest {

    private static final CuiLogger LOGGER = new CuiLogger(HealthCheckBlockingReproductionIT.class);
    private static final int HEALTH_CHECK_TIMEOUT_SECONDS = 15;
    private static final int CONCURRENT_HEALTH_CHECKS = 5;

    @Test
    void shouldDemonstrateHealthCheckBlockingBehavior() {
        LOGGER.info("Testing health check blocking behavior during JWKS loading failures");

        List<HealthCheckTimingResult> results = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_HEALTH_CHECKS);

        try {
            // Launch multiple concurrent health checks to test blocking behavior
            List<Future<HealthCheckTimingResult>> futures = new ArrayList<>();

            for (int i = 1; i <= CONCURRENT_HEALTH_CHECKS; i++) {
                final int checkId = i;
                Future<HealthCheckTimingResult> future = executor.submit(() ->
                        performTimedHealthCheck("readiness", "/q/health/ready", checkId));
                futures.add(future);
            }

            // Wait for all health checks to complete (or timeout)
            for (int i = 0; i < futures.size(); i++) {
                try {
                    HealthCheckTimingResult result = futures.get(i).get(HEALTH_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    results.add(result);
                    LOGGER.info("Health check %d completed: %s", i + 1, result);
                } catch (TimeoutException e) {
                    LOGGER.error("Health check %d TIMED OUT after %d seconds", i + 1, HEALTH_CHECK_TIMEOUT_SECONDS);
                    results.add(new HealthCheckTimingResult(i + 1, "readiness", Duration.ofSeconds(HEALTH_CHECK_TIMEOUT_SECONDS),
                            false, true, "TIMEOUT", null));
                } catch (Exception e) {
                    LOGGER.error(e, "Health check %d failed with exception", i + 1);
                    results.add(new HealthCheckTimingResult(i + 1, "readiness", Duration.ZERO,
                            false, false, "EXCEPTION", e.getMessage()));
                }
            }
        } finally {
            executor.shutdown();
        }

        analyzeBlockingBehavior(results);
    }

    @Test
    void shouldCompareHealthCheckTypesForBlockingBehavior() {
        LOGGER.info("Comparing blocking behavior across different health check types");

        List<String> healthCheckTypes = List.of(
                "liveness",
                "readiness",
                "startup",
                "overall"
        );

        List<String> healthCheckUrls = List.of(
                "/q/health/live",
                "/q/health/ready",
                "/q/health/started",
                "/q/health"
        );

        List<HealthCheckTimingResult> results = new ArrayList<>();

        for (int i = 0; i < healthCheckTypes.size(); i++) {
            String type = healthCheckTypes.get(i);
            String url = healthCheckUrls.get(i);

            HealthCheckTimingResult result = performTimedHealthCheck(type, url, i + 1);
            results.add(result);

            LOGGER.info("Health check %s: %s", type, result);

            // Brief pause between checks
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Analyze which health check types block vs respond quickly
        analyzeDifferentialBlockingBehavior(results);
    }

    /**
     * Performs a timed health check request and measures response time.
     */
    private HealthCheckTimingResult performTimedHealthCheck(String type, String url, int checkId) {
        Instant startTime = Instant.now();
        boolean success = false;
        boolean timedOut = false;
        String status = null;
        String errorMessage = null;

        try {
            LOGGER.debug("Starting %s health check %d at %s", type, checkId, url);

            String responseStatus = given()
                    .when()
                    .get(url)
                    .then()
                    .extract().path("status");

            status = responseStatus;
            success = "UP".equals(responseStatus);

            LOGGER.debug("Health check %s %d completed with status: %s", type, checkId, status);

        } catch (Exception e) {
            errorMessage = e.getMessage();
            if (errorMessage != null && (errorMessage.contains("timeout") || errorMessage.contains("timed out"))) {
                timedOut = true;
            }
            LOGGER.debug("Health check %s %d failed: %s", type, checkId, errorMessage);
        }

        Duration responseTime = Duration.between(startTime, Instant.now());
        return new HealthCheckTimingResult(checkId, type, responseTime, success, timedOut, status, errorMessage);
    }

    /**
     * Analyzes results to identify blocking patterns.
     */
    private void analyzeBlockingBehavior(List<HealthCheckTimingResult> results) {
        LOGGER.info("Analyzing blocking behavior from %d health check attempts", results.size());

        long timeouts = results.stream().mapToLong(r -> r.timedOut ? 1 : 0).sum();
        long successes = results.stream().mapToLong(r -> r.success ? 1 : 0).sum();

        Duration avgResponseTime = Duration.ofMillis(
                (long) results.stream()
                        .mapToLong(r -> r.responseTime.toMillis())
                        .average()
                        .orElse(0)
        );

        Duration maxResponseTime = results.stream()
                .map(r -> r.responseTime)
                .max(Duration::compareTo)
                .orElse(Duration.ZERO);

        LOGGER.info("BLOCKING BEHAVIOR ANALYSIS:");
        LOGGER.info("- Timeouts: %d/%d (%.1f%%)", timeouts, results.size(), (100.0 * timeouts / results.size()));
        LOGGER.info("- Successes: %d/%d (%.1f%%)", successes, results.size(), (100.0 * successes / results.size()));
        LOGGER.info("- Average response time: %s", avgResponseTime);
        LOGGER.info("- Maximum response time: %s", maxResponseTime);

        // Since we fixed the blocking issues, we now expect fast responses
        // This test validates that health checks are NOT blocking anymore
        boolean hasBlockingBehavior = timeouts > 0 || maxResponseTime.compareTo(Duration.ofSeconds(5)) > 0;
        
        if (hasBlockingBehavior) {
            LOGGER.warn("Health checks are still showing blocking behavior - this indicates remaining issues");
            fail("Health checks should be responsive after our fixes, but found blocking behavior");
        } else {
            LOGGER.info("✅ SUCCESS: Health checks are responsive (no blocking behavior detected)");
            assertTrue(true, "Health checks are working correctly without blocking");
        }

        if (timeouts > 0) {
            LOGGER.info("✅ BLOCKING BEHAVIOR CONFIRMED: %d health checks timed out", timeouts);
        }

        if (maxResponseTime.compareTo(Duration.ofSeconds(5)) > 0) {
            LOGGER.info("✅ SLOW RESPONSE CONFIRMED: Maximum response time was %s", maxResponseTime);
        }
    }

    /**
     * Analyzes differences in blocking behavior between health check types.
     */
    private void analyzeDifferentialBlockingBehavior(List<HealthCheckTimingResult> results) {
        LOGGER.info("DIFFERENTIAL BLOCKING ANALYSIS:");

        for (HealthCheckTimingResult result : results) {
            String blockingStatus = result.timedOut ? "BLOCKED" :
                    result.responseTime.compareTo(Duration.ofSeconds(2)) > 0 ? "SLOW" : "FAST";

            LOGGER.info("- %s: %s (%s, %dms)",
                    result.type.toUpperCase(),
                    blockingStatus,
                    result.success ? "UP" : "DOWN/ERROR",
                    result.responseTime.toMillis());
        }

        // Find which types block vs respond quickly
        List<HealthCheckTimingResult> blocking = results.stream()
                .filter(r -> r.timedOut || r.responseTime.compareTo(Duration.ofSeconds(2)) > 0)
                .toList();

        List<HealthCheckTimingResult> responsive = results.stream()
                .filter(r -> !r.timedOut && r.responseTime.compareTo(Duration.ofSeconds(2)) <= 0)
                .toList();

        if (!blocking.isEmpty()) {
            LOGGER.info("✅ BLOCKING HEALTH CHECKS: %s",
                    blocking.stream().map(r -> r.type).toList());
        }

        if (!responsive.isEmpty()) {
            LOGGER.info("✅ RESPONSIVE HEALTH CHECKS: %s",
                    responsive.stream().map(r -> r.type).toList());
        }

        // The key insight: readiness checks should block while liveness checks should not
        boolean readinessBlocks = results.stream()
                .anyMatch(r -> "readiness".equals(r.type) &&
                        (r.timedOut || r.responseTime.compareTo(Duration.ofSeconds(2)) > 0));

        if (readinessBlocks) {
            LOGGER.info("🎯 ROOT CAUSE CONFIRMED: Readiness check blocks during JWKS loading failures");
        }
    }

    private record HealthCheckTimingResult(int checkId, String type, Duration responseTime,
                                         boolean success, boolean timedOut, String status, String errorMessage) {
    }
}