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

import de.cuioss.jwt.benchmarking.common.model.BenchmarkType;

import java.util.List;

/**
 * Container for comprehensive benchmark metrics data.
 * Used for JSON serialization and historical performance tracking.
 * 
 * @author CUI Benchmarking Infrastructure
 */
public class BenchmarkMetrics {
    
    private final String timestamp;
    private final BenchmarkType type;
    private final List<BenchmarkResult> results;
    private final double avgThroughput;
    private final double avgLatency;
    private final long performanceScore;
    
    public BenchmarkMetrics(String timestamp, BenchmarkType type, List<BenchmarkResult> results,
                          double avgThroughput, double avgLatency, long performanceScore) {
        this.timestamp = timestamp;
        this.type = type;
        this.results = results;
        this.avgThroughput = avgThroughput;
        this.avgLatency = avgLatency;
        this.performanceScore = performanceScore;
    }
    
    // Getters for JSON serialization
    public String getTimestamp() { return timestamp; }
    public BenchmarkType getType() { return type; }
    public List<BenchmarkResult> getResults() { return results; }
    public double getAvgThroughput() { return avgThroughput; }
    public double getAvgLatency() { return avgLatency; }
    public long getPerformanceScore() { return performanceScore; }
}