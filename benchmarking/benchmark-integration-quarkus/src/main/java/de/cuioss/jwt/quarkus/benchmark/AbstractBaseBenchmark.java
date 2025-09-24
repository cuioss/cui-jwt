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
package de.cuioss.jwt.quarkus.benchmark;

import de.cuioss.benchmarking.common.base.AbstractBenchmarkBase;
import de.cuioss.benchmarking.common.config.IntegrationConfiguration;
import org.openjdk.jmh.annotations.*;

import java.net.http.HttpRequest;
import java.util.concurrent.TimeUnit;

/**
 * Abstract base class for all Quarkus benchmarks (with or without authentication).
 * Provides common setup, configuration, and utility methods.
 *
 * <p>For benchmarks that require JWT authentication, use {@link AbstractIntegrationBenchmark} instead.</p>
 *
 * <p>Benchmark execution parameters (iterations, threads, warmup, etc.) are configured dynamically
 * via {@link QuarkusIntegrationRunner} and {@link de.cuioss.benchmarking.common.config.BenchmarkConfiguration} using system properties.</p>
 *
 * @since 1.0
 */
@BenchmarkMode(Mode.All) @OutputTimeUnit(TimeUnit.MILLISECONDS) @State(Scope.Benchmark) public abstract class AbstractBaseBenchmark extends AbstractBenchmarkBase {

    protected String quarkusMetricsUrl;

    /**
     * Setup method called once before all benchmark iterations.
     * Calls the base setup and initializes Quarkus-specific components.
     */
    @Setup(Level.Trial) public void setupBenchmark() {
        logger.debug("Starting setupBenchmark for {}", this.getClass().getSimpleName());
        // Call base setup
        setupBase();
        logger.debug("Base setup completed for {}", this.getClass().getSimpleName());
    }

    /**
     * Performs additional setup specific to Quarkus integration benchmarks.
     * Initializes metrics exporter and configuration.
     */
    @Override protected void performAdditionalSetup() {
        // Load integration configuration from system properties
        IntegrationConfiguration integrationConfig = IntegrationConfiguration.fromProperties();

        // Extract URLs from configuration
        serviceUrl = integrationConfig.integrationServiceUrl();
        quarkusMetricsUrl = integrationConfig.metricsUrl();

        logger.debug("Service URL: {}", serviceUrl);
        logger.debug("Quarkus Metrics URL: {}", quarkusMetricsUrl);
    }


    /**
     * Creates a basic HTTP request builder with common headers for Quarkus endpoints.
     *
     * @param path the path to append to the service URL
     * @return configured request builder
     */
    protected HttpRequest.Builder createRequestForPath(String path) {
        return createBaseRequest(serviceUrl, path);
    }

    /**
     * Export metrics implementation for Quarkus benchmarks.
     * Subclasses can override this to export specific metrics.
     */
    @Override public void exportBenchmarkMetrics() {
        // Default implementation - subclasses can override
        logger.debug("Metrics export completed for Quarkus benchmark");
    }

}