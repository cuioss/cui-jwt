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
package de.cuioss.jwt.benchmarking.common.badge;

import de.cuioss.jwt.benchmarking.common.model.BenchmarkType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.openjdk.jmh.results.RunResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;

/**
 * Generator for Shields.io compatible badges during JMH benchmark execution.
 * Creates performance badges, trend badges, and last-run timestamps directly
 * from benchmark results without requiring post-processing scripts.
 * 
 * @author CUI Benchmarking Infrastructure
 */
public class BadgeGenerator {
    
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final DecimalFormat throughputFormat = new DecimalFormat("#,###");
    private final DecimalFormat latencyFormat = new DecimalFormat("#,###.###");
    
    /**
     * Generate all badges for the benchmark results.
     */
    public void generateAllBadges(Collection<RunResult> results, BenchmarkType type, String outputDir) throws IOException {
        Path badgeDir = Paths.get(outputDir);
        Files.createDirectories(badgeDir);
        
        generatePerformanceBadge(results, type, outputDir);
        generateTrendBadge(results, type, outputDir);
        generateLastRunBadge(outputDir);
        
        System.out.println("🏷️ Generated " + type.getDisplayName().toLowerCase() + " badges in " + outputDir);
    }
    
    /**
     * Generate a performance badge showing current benchmark score.
     */
    public void generatePerformanceBadge(Collection<RunResult> results, BenchmarkType type, String outputDir) throws IOException {
        PerformanceScore score = calculatePerformanceScore(results, type);
        
        Badge badge = Badge.builder()
            .schemaVersion(1)
            .label(type.getBadgeLabel())
            .message(formatPerformanceMessage(score, type))
            .color(getColorForScore(score, type))
            .build();
            
        writeBadgeFile(badge, outputDir, type.getBadgeFilename());
    }
    
    /**
     * Generate a trend badge showing performance direction.
     */
    public void generateTrendBadge(Collection<RunResult> results, BenchmarkType type, String outputDir) throws IOException {
        // For initial implementation, we'll show a stable trend
        // In a full implementation, this would compare with historical data
        TrendAnalysis trend = analyzeTrend(results);
        
        Badge badge = Badge.builder()
            .schemaVersion(1)
            .label("Performance Trend")
            .message(formatTrendMessage(trend))
            .color(getTrendColor(trend))
            .build();
            
        writeBadgeFile(badge, outputDir, type.getTrendBadgeFilename());
    }
    
    /**
     * Generate a last-run timestamp badge.
     */
    public void generateLastRunBadge(String outputDir) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy"));
        
        Badge badge = Badge.builder()
            .schemaVersion(1)
            .label("Last Benchmark")
            .message(timestamp)
            .color("blue")
            .build();
            
        writeBadgeFile(badge, outputDir, "last-run-badge.json");
    }
    
    /**
     * Calculate aggregate performance score from benchmark results.
     */
    private PerformanceScore calculatePerformanceScore(Collection<RunResult> results, BenchmarkType type) {
        if (results.isEmpty()) {
            return new PerformanceScore(0.0, 0.0, 0, "No Results");
        }
        
        double totalThroughput = 0.0;
        double totalLatency = 0.0;
        int validResults = 0;
        
        for (RunResult result : results) {
            if (result.getPrimaryResult() != null) {
                double score = result.getPrimaryResult().getScore();
                totalThroughput += score;
                
                // Estimate latency (inverse relationship for throughput benchmarks)
                if (score > 0) {
                    totalLatency += (1.0 / score) * 1000; // Convert to milliseconds
                    validResults++;
                }
            }
        }
        
        double avgThroughput = validResults > 0 ? totalThroughput / validResults : 0.0;
        double avgLatency = validResults > 0 ? totalLatency / validResults : 0.0;
        
        long compositeScore;
        String scoreLabel;
        
        if (type == BenchmarkType.MICRO) {
            compositeScore = Math.round(avgThroughput);
            scoreLabel = "ops/s";
        } else {
            // For integration benchmarks, show throughput with context
            compositeScore = Math.round(avgThroughput);
            scoreLabel = "req/s";
        }
        
        return new PerformanceScore(avgThroughput, avgLatency, compositeScore, scoreLabel);
    }
    
    /**
     * Format the performance message for the badge.
     */
    private String formatPerformanceMessage(PerformanceScore score, BenchmarkType type) {
        if (score.getCompositeScore() == 0) {
            return "No Data";
        }
        
        String formattedScore = throughputFormat.format(score.getCompositeScore());
        return formattedScore + " " + score.getScoreLabel();
    }
    
    /**
     * Determine badge color based on performance score.
     */
    private String getColorForScore(PerformanceScore score, BenchmarkType type) {
        long compositeScore = score.getCompositeScore();
        
        if (compositeScore == 0) {
            return "lightgrey";
        }
        
        if (type == BenchmarkType.MICRO) {
            // Micro benchmark thresholds (ops/sec)
            return compositeScore > 1000000 ? "brightgreen" :
                   compositeScore > 100000 ? "green" :
                   compositeScore > 10000 ? "yellow" :
                   compositeScore > 1000 ? "orange" : "red";
        } else {
            // Integration benchmark thresholds (req/sec)
            return compositeScore > 10000 ? "brightgreen" :
                   compositeScore > 1000 ? "green" :
                   compositeScore > 100 ? "yellow" :
                   compositeScore > 10 ? "orange" : "red";
        }
    }
    
    /**
     * Analyze performance trend (simplified implementation).
     */
    private TrendAnalysis analyzeTrend(Collection<RunResult> results) {
        // Simplified trend analysis - in a full implementation this would
        // compare with historical results
        return new TrendAnalysis(true, 0.0, "Stable");
    }
    
    /**
     * Format trend message for badge.
     */
    private String formatTrendMessage(TrendAnalysis trend) {
        return trend.getDescription();
    }
    
    /**
     * Get color for trend badge.
     */
    private String getTrendColor(TrendAnalysis trend) {
        return trend.isImproving() ? "brightgreen" : "yellow";
    }
    
    /**
     * Write badge to JSON file.
     */
    private void writeBadgeFile(Badge badge, String outputDir, String filename) throws IOException {
        Path badgePath = Paths.get(outputDir, filename);
        String json = gson.toJson(badge);
        Files.write(badgePath, json.getBytes());
    }
}