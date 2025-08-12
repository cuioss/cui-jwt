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
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.openjdk.jmh.results.RunResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Generator for structured performance metrics in JSON format.
 * Creates comprehensive performance data for visualization and historical tracking.
 * 
 * @author CUI Benchmarking Infrastructure
 */
public class MetricsGenerator {
    
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    /**
     * Generate comprehensive metrics JSON for the benchmark results.
     */
    public void generateMetricsJson(Collection<RunResult> results, BenchmarkType type, String outputDir) throws IOException {
        Path metricsDir = Paths.get(outputDir);
        Files.createDirectories(metricsDir);
        
        BenchmarkMetrics metrics = createMetrics(results, type);
        
        String filename = type.getMetricsPrefix() + "-metrics.json";
        Path metricsFile = metricsDir.resolve(filename);
        
        String json = gson.toJson(metrics);
        Files.write(metricsFile, json.getBytes());
        
        System.out.println("📈 Generated metrics file: " + filename);
    }
    
    /**
     * Create comprehensive metrics from benchmark results.
     */
    private BenchmarkMetrics createMetrics(Collection<RunResult> results, BenchmarkType type) {
        List<BenchmarkResult> benchmarkResults = new ArrayList<>();
        double totalThroughput = 0.0;
        double totalLatency = 0.0;
        int validResults = 0;
        
        for (RunResult result : results) {
            if (result.getPrimaryResult() != null) {
                double score = result.getPrimaryResult().getScore();
                double error = result.getPrimaryResult().getScoreError();
                
                // Extract benchmark name
                String fullBenchmark = result.getParams().getBenchmark();
                String benchmarkName = extractBenchmarkName(fullBenchmark);
                
                // Calculate latency estimate (inverse of throughput for ops/sec)
                double latency = score > 0 ? (1.0 / score) * 1000 : 0.0; // Convert to milliseconds
                
                BenchmarkResult benchResult = new BenchmarkResult(
                    benchmarkName,
                    score,
                    error,
                    latency,
                    result.getPrimaryResult().getScoreUnit()
                );
                
                benchmarkResults.add(benchResult);
                totalThroughput += score;
                totalLatency += latency;
                validResults++;
            }
        }
        
        // Calculate aggregates
        double avgThroughput = validResults > 0 ? totalThroughput / validResults : 0.0;
        double avgLatency = validResults > 0 ? totalLatency / validResults : 0.0;
        long performanceScore = calculatePerformanceScore(avgThroughput, avgLatency, type);
        
        return new BenchmarkMetrics(
            Instant.now().toString(),
            type,
            benchmarkResults,
            avgThroughput,
            avgLatency,
            performanceScore
        );
    }
    
    /**
     * Extract a clean benchmark name from the full class path.
     */
    private String extractBenchmarkName(String fullBenchmark) {
        int lastDot = fullBenchmark.lastIndexOf('.');
        return lastDot >= 0 ? fullBenchmark.substring(lastDot + 1) : fullBenchmark;
    }
    
    /**
     * Calculate composite performance score.
     */
    private long calculatePerformanceScore(double throughput, double latency, BenchmarkType type) {
        if (type == BenchmarkType.MICRO) {
            // For micro benchmarks, emphasize throughput
            return Math.round(throughput);
        } else {
            // For integration benchmarks, balance throughput and responsiveness
            return latency > 0 ? Math.round(throughput / (latency / 1000)) : Math.round(throughput);
        }
    }
}