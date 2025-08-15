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
package de.cuioss.benchmarking.model;

/**
 * Represents the type of benchmark being executed.
 * <p>
 * Different benchmark types may require different processing logic,
 * badge generation strategies, and performance thresholds.
 *
 * @since 1.0.0
 */
public enum BenchmarkType {
    
    /**
     * Micro-benchmarks that test individual components or methods in isolation.
     * These typically have very high throughput and low latency measurements.
     */
    MICRO,
    
    /**
     * Integration benchmarks that test complete application flows including
     * network communication, containerization, and realistic workloads.
     * These typically have lower throughput and higher latency than micro-benchmarks.
     */
    INTEGRATION
}