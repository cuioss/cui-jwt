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
package de.cuioss.benchmarking.common.validation;

import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.results.RunResult;

import java.util.Collection;

/**
 * Validates benchmark results to ensure they contain valid data.
 * 
 * @since 1.0
 */
public final class BenchmarkResultValidator {

    private static final CuiLogger LOGGER = new CuiLogger(BenchmarkResultValidator.class);

    private BenchmarkResultValidator() {
        // Utility class
    }

    /**
     * Validates that benchmark results are present and contain valid data.
     * 
     * @param results the collection of benchmark results to validate
     * @throws IllegalStateException if results are empty or contain invalid data
     */
    public static void validateResults(Collection<RunResult> results) {
        if (results == null || results.isEmpty()) {
            throw new IllegalStateException("Benchmark execution failed: No results produced");
        }

        LOGGER.debug("Validating {} benchmark results", results.size());

        long benchmarksWithoutResults = results.stream()
                .filter(BenchmarkResultValidator::hasInvalidResult)
                .count();

        if (benchmarksWithoutResults > 0) {
            throw new IllegalStateException("Benchmark execution failed: %d out of %d benchmarks produced no valid results".formatted(
                    benchmarksWithoutResults, results.size()));
        }

        LOGGER.debug("All {} benchmark results are valid", results.size());
    }

    /**
     * Checks if a single result is invalid.
     * 
     * @param result the result to check
     * @return true if the result is invalid, false otherwise
     */
    private static boolean hasInvalidResult(RunResult result) {
        return result.getPrimaryResult() == null ||
                result.getPrimaryResult().getStatistics() == null ||
                result.getPrimaryResult().getStatistics().getN() == 0;
    }

    /**
     * Validates that at least one benchmark result is present.
     * This is a lighter validation for cases where partial results are acceptable.
     * 
     * @param results the collection of benchmark results to validate
     * @return true if at least one valid result exists
     */
    public static boolean hasValidResults(Collection<RunResult> results) {
        if (results == null || results.isEmpty()) {
            return false;
        }

        return results.stream()
                .anyMatch(result -> !hasInvalidResult(result));
    }
}