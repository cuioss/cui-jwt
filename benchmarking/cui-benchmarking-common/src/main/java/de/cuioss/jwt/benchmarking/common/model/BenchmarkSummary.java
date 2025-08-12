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
package de.cuioss.jwt.benchmarking.common.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.openjdk.jmh.results.RunResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collection;

/**
 * Summary of benchmark execution results for CI/CD consumption and historical tracking.
 * Provides a high-level overview of benchmark performance and statistics.
 * 
 * @author CUI Benchmarking Infrastructure
 */
public class BenchmarkSummary {
    
    private final String timestamp;
    private final BenchmarkType type;
    private final int benchmarkCount;
    private final double avgThroughput;
    private final double avgLatency;
    private final long performanceScore;
    private final String status;
    
    public BenchmarkSummary(Collection<RunResult> results, BenchmarkType type) {
        this.timestamp = Instant.now().toString();
        this.type = type;
        this.benchmarkCount = results.size();
        
        // Calculate aggregate performance metrics
        double totalThroughput = 0.0;
        double totalLatency = 0.0;
        int validResults = 0;
        
        for (RunResult result : results) {
            if (result.getPrimaryResult() != null) {
                double score = result.getPrimaryResult().getScore();
                totalThroughput += score;
                
                // Estimate latency (inverse of throughput for ops/sec)
                if (score > 0) {
                    totalLatency += (1.0 / score);
                    validResults++;
                }
            }
        }
        
        this.avgThroughput = validResults > 0 ? totalThroughput / validResults : 0.0;
        this.avgLatency = validResults > 0 ? totalLatency / validResults : 0.0;
        this.performanceScore = calculatePerformanceScore(avgThroughput, avgLatency);
        this.status = determineStatus();
    }
    
    /**
     * Calculate a composite performance score for the benchmark type.
     */
    private long calculatePerformanceScore(double throughput, double latency) {
        if (type == BenchmarkType.MICRO) {
            // For micro benchmarks, emphasize throughput (ops/sec)
            return Math.round(throughput);
        } else {
            // For integration benchmarks, balance throughput and latency
            // Score = throughput / latency (higher is better)
            return latency > 0 ? Math.round(throughput / latency) : Math.round(throughput);
        }
    }
    
    /**
     * Determine the status of the benchmark run based on performance thresholds.
     */
    private String determineStatus() {
        if (benchmarkCount == 0) {
            return "NO_BENCHMARKS";
        }
        
        if (type == BenchmarkType.MICRO) {
            // Micro benchmarks should achieve high throughput
            return avgThroughput > 100000 ? "EXCELLENT" : 
                   avgThroughput > 10000 ? "GOOD" : 
                   avgThroughput > 1000 ? "ACCEPTABLE" : "POOR";
        } else {
            // Integration benchmarks have lower but still meaningful throughput
            return avgThroughput > 1000 ? "EXCELLENT" :
                   avgThroughput > 100 ? "GOOD" :
                   avgThroughput > 10 ? "ACCEPTABLE" : "POOR";
        }
    }
    
    /**
     * Write the benchmark summary to a JSON file.
     */
    public void writeToFile(String filepath) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(this);
        Files.write(Paths.get(filepath), json.getBytes());
    }
    
    // Getters for JSON serialization
    public String getTimestamp() { return timestamp; }
    public BenchmarkType getType() { return type; }
    public int getBenchmarkCount() { return benchmarkCount; }
    public double getAvgThroughput() { return avgThroughput; }
    public double getAvgLatency() { return avgLatency; }
    public long getPerformanceScore() { return performanceScore; }
    public String getStatus() { return status; }
}