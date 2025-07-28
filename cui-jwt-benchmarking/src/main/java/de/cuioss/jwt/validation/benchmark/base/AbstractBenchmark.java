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
import de.cuioss.jwt.validation.benchmark.BenchmarkMetricsAggregator;
import de.cuioss.jwt.validation.benchmark.BenchmarkMetricsCollector;
import de.cuioss.jwt.validation.benchmark.TokenRepository;
import de.cuioss.jwt.validation.metrics.TokenValidatorMonitor;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Level;

/**
 * Abstract base class for all JWT benchmarks.
 * Provides common functionality to reduce code duplication.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public abstract class AbstractBenchmark {
    
    protected TokenRepository tokenRepository;
    protected TokenValidator tokenValidator;
    
    /**
     * Get the array of benchmark method names for this benchmark class.
     * Subclasses must implement this to provide their benchmark method names.
     * 
     * @return Array of benchmark method names
     */
    protected abstract String[] getBenchmarkMethodNames();
    
    /**
     * Setup method for benchmark initialization.
     * Subclasses should call this from their @Setup method.
     * 
     * @param benchmarkNames Names to register with BenchmarkMetricsAggregator
     */
    protected void setupBase(String... benchmarkNames) {
        // Register benchmarks for metrics collection
        BenchmarkMetricsAggregator.registerBenchmarks(benchmarkNames);
        
        // Initialize token repository
        tokenRepository = new TokenRepository();
        
        // Create token validator
        tokenValidator = tokenRepository.createTokenValidator();
    }
    
    /**
     * Common metrics collection method.
     * Called automatically at the end of each iteration.
     */
    @TearDown(Level.Iteration)
    public void collectMetrics() {
        if (tokenValidator != null) {
            TokenValidatorMonitor monitor = tokenValidator.getPerformanceMonitor();
            String currentBenchmarkName = BenchmarkMetricsCollector.getCurrentBenchmarkName(getBenchmarkMethodNames());
            BenchmarkMetricsCollector.collectIterationMetrics(monitor, currentBenchmarkName);
        }
    }
}