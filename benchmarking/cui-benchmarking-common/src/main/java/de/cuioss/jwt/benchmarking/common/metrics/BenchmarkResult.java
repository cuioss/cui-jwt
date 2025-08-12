/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.cuioss.jwt.benchmarking.common.metrics;

/**
 * Individual benchmark result for metrics tracking.
 * Contains performance data for a single benchmark method.
 * 
 * @author CUI Benchmarking Infrastructure
 */
public class BenchmarkResult {
    
    private final String name;
    private final double throughput;
    private final double error;
    private final double latency;
    private final String unit;
    
    public BenchmarkResult(String name, double throughput, double error, double latency, String unit) {
        this.name = name;
        this.throughput = throughput;
        this.error = error;
        this.latency = latency;
        this.unit = unit;
    }
    
    // Getters for JSON serialization
    public String getName() { return name; }
    public double getThroughput() { return throughput; }
    public double getError() { return error; }
    public double getLatency() { return latency; }
    public String getUnit() { return unit; }
}