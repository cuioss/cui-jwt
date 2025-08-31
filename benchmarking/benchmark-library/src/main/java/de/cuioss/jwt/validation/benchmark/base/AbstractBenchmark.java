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
package de.cuioss.jwt.validation.benchmark.base;

import de.cuioss.benchmarking.common.base.AbstractBenchmarkBase;
import de.cuioss.jwt.validation.TokenValidator;
import de.cuioss.jwt.validation.benchmark.MockTokenRepository;
import de.cuioss.jwt.validation.benchmark.SimplifiedMetricsExporter;
import de.cuioss.jwt.validation.metrics.TokenValidatorMonitorConfig;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.TearDown;

import java.io.IOException;

/**
 * Abstract base class for all JWT library benchmarks.
 * Extends the common base class and provides library-specific functionality.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public abstract class AbstractBenchmark extends AbstractBenchmarkBase {

    protected MockTokenRepository tokenRepository;
    protected TokenValidator tokenValidator;

    /**
     * Performs additional setup specific to library benchmarks.
     * Initializes token repository and validator.
     */
    @Override protected void performAdditionalSetup() {
        // Initialize token repository with cache size configured for 10% of tokens
        MockTokenRepository.Config config = MockTokenRepository.Config.builder()
                .cacheSize(60) // 10% of default 600 tokens
                .build();
        tokenRepository = new MockTokenRepository(config);

        // Create token validator with cache configuration
        tokenValidator = tokenRepository.createTokenValidator(
                TokenValidatorMonitorConfig.builder()
                        .measurementTypes(TokenValidatorMonitorConfig.ALL_MEASUREMENT_TYPES)
                        .windowSize(10000)
                        .build(),
                config);
    }

    /**
     * Export metrics directly from this benchmark's monitor.
     * Called at the end of each benchmark trial.
     */
    @TearDown(Level.Trial) @Override public void exportBenchmarkMetrics() {
        if (tokenValidator != null) {
            try {
                SimplifiedMetricsExporter.exportMetrics(tokenValidator.getPerformanceMonitor());
            } catch (IOException e) {
                logger.error("Failed to export benchmark metrics", e);
            }
        }
    }
}