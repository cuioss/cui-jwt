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

import de.cuioss.jwt.validation.TokenValidator;
import de.cuioss.jwt.validation.benchmark.SimplifiedMetricsExporter;
import de.cuioss.jwt.validation.benchmark.TokenRepository;
import de.cuioss.jwt.validation.metrics.TokenValidatorMonitorConfig;
import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.TearDown;

import java.io.IOException;

/**
 * Abstract base class for all JWT benchmarks.
 * Provides common functionality to reduce code duplication.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public abstract class AbstractBenchmark {

    private static final CuiLogger log = new CuiLogger(AbstractBenchmark.class);

    protected TokenRepository tokenRepository;
    protected TokenValidator tokenValidator;

    /**
     * Setup method for benchmark initialization.
     * Subclasses should call this from their @Setup method.
     */
    protected void setupBase() {
        // Initialize token repository with cache size configured for 10% of tokens
        TokenRepository.Config config = TokenRepository.Config.builder()
                .cacheSize(60) // 10% of default 600 tokens
                .build();
        tokenRepository = new TokenRepository(config);

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
    @TearDown(Level.Trial) public void exportBenchmarkMetrics() {
        if (tokenValidator != null) {
            try {
                SimplifiedMetricsExporter.exportMetrics(tokenValidator.getPerformanceMonitor());
            } catch (IOException e) {
                log.error("Failed to export benchmark metrics", e);
            }
        }
    }
}