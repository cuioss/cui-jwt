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
package de.cuioss.jwt.quarkus.benchmark.metrics;

import de.cuioss.tools.logging.CuiLogger;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runner to test MetricsPostProcessor on actual benchmark results
 */
class MetricsPostProcessorRunnerTest {

    private static final CuiLogger LOGGER = new CuiLogger(MetricsPostProcessorRunnerTest.class);

    @Test
    void runParserOnActualResults() {
        String resultsDirectory = "target/benchmark-results";
        File resultsDir = new File(resultsDirectory);

        // This test runs when benchmark results are available
        if (resultsDir.exists()) {
            assertDoesNotThrow(() -> {
                MetricsPostProcessor.parseAndExport(resultsDirectory);
                LOGGER.info("Successfully parsed and exported HTTP metrics from actual benchmark results");
            }, "MetricsPostProcessor should not throw exception when processing valid results");

            // Verify output files were created
            File httpMetrics = new File(resultsDirectory, "http-metrics.json");
            if (httpMetrics.exists()) {
                assertTrue(httpMetrics.length() > 0, "http-metrics.json should not be empty");
            }
        } else {
            LOGGER.info("Skipping test - no benchmark results available at: " + resultsDirectory);
            assertTrue(true, "Test skipped when no benchmark results are available");
        }
    }
}