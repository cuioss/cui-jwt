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
package de.cuioss.jwt.benchmarking.common;

import de.cuioss.jwt.benchmarking.common.badge.BadgeGenerator;
import de.cuioss.jwt.benchmarking.common.github.GitHubPagesGenerator;
import de.cuioss.jwt.benchmarking.common.metrics.MetricsGenerator;
import de.cuioss.jwt.benchmarking.common.report.ReportGenerator;
import de.cuioss.jwt.benchmarking.common.model.BenchmarkType;
import de.cuioss.jwt.benchmarking.common.model.BenchmarkSummary;
import org.openjdk.jmh.results.RunResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;

/**
 * Comprehensive processor for JMH benchmark results that generates all artifacts
 * during the benchmark execution phase. This eliminates the need for post-processing
 * in shell scripts and ensures consistent artifact generation.
 * 
 * @author CUI Benchmarking Infrastructure
 */
public class BenchmarkResultProcessor {

    /**
     * Process benchmark results and generate all artifacts in a single operation.
     * 
     * @param results JMH benchmark results
     * @param outputDir output directory for all artifacts
     * @param generateBadges whether to generate shields.io badges
     * @param generateReports whether to generate HTML reports
     * @param generateGitHubPages whether to prepare GitHub Pages deployment
     */
    public void processResults(Collection<RunResult> results, String outputDir, 
                             boolean generateBadges, boolean generateReports, 
                             boolean generateGitHubPages) {
        
        try {
            System.out.println("📊 Processing " + results.size() + " benchmark results");
            
            // Ensure output directories exist
            createDirectories(outputDir);
            
            // Detect benchmark type from results
            BenchmarkType type = detectBenchmarkType(results);
            System.out.println("🔍 Detected benchmark type: " + type);
            
            // Generate performance badges
            if (generateBadges) {
                System.out.println("🏷️ Generating performance badges...");
                BadgeGenerator badgeGen = new BadgeGenerator();
                badgeGen.generateAllBadges(results, type, outputDir + "/badges");
                System.out.println("✅ Badges generated successfully");
            }
            
            // Generate performance metrics
            System.out.println("📈 Generating performance metrics...");
            MetricsGenerator metricsGen = new MetricsGenerator();
            metricsGen.generateMetricsJson(results, type, outputDir + "/data");
            System.out.println("✅ Metrics generated successfully");
            
            // Generate HTML reports
            if (generateReports) {
                System.out.println("📄 Generating HTML reports...");
                ReportGenerator reportGen = new ReportGenerator();
                reportGen.generateAllReports(results, type, outputDir);
                System.out.println("✅ Reports generated successfully");
            }
            
            // Prepare GitHub Pages deployment structure
            if (generateGitHubPages) {
                System.out.println("📚 Preparing GitHub Pages deployment...");
                GitHubPagesGenerator ghGen = new GitHubPagesGenerator();
                ghGen.prepareDeploymentStructure(outputDir, outputDir + "/gh-pages-ready");
                System.out.println("✅ GitHub Pages structure prepared");
            }
            
            // Write benchmark summary for CI/CD
            writeBenchmarkSummary(results, type, outputDir);
            
            System.out.println("🎯 All benchmark artifacts generated successfully");
            
        } catch (Exception e) {
            System.err.println("❌ Error processing benchmark results: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Benchmark result processing failed", e);
        }
    }
    
    /**
     * Smart detection of benchmark type based on package structure and class names.
     * This approach is more robust than hardcoded class name matching.
     */
    private BenchmarkType detectBenchmarkType(Collection<RunResult> results) {
        return results.stream()
            .map(r -> r.getParams().getBenchmark())
            .findFirst()
            .map(benchmark -> {
                String lowerBenchmark = benchmark.toLowerCase();
                
                // Check for integration benchmark indicators
                if (lowerBenchmark.contains(".integration.") || 
                    lowerBenchmark.contains(".quarkus.") ||
                    lowerBenchmark.contains("integration") ||
                    lowerBenchmark.contains("endpoint") ||
                    lowerBenchmark.contains("health")) {
                    return BenchmarkType.INTEGRATION;
                }
                
                // Default to micro benchmark
                return BenchmarkType.MICRO;
            })
            .orElse(BenchmarkType.MICRO);
    }
    
    /**
     * Create all necessary output directories.
     */
    private void createDirectories(String outputDir) throws IOException {
        Path basePath = Paths.get(outputDir);
        Files.createDirectories(basePath);
        Files.createDirectories(basePath.resolve("badges"));
        Files.createDirectories(basePath.resolve("data"));
        Files.createDirectories(basePath.resolve("reports"));
        Files.createDirectories(basePath.resolve("gh-pages-ready"));
    }
    
    /**
     * Write a comprehensive benchmark summary for CI/CD systems.
     */
    private void writeBenchmarkSummary(Collection<RunResult> results, BenchmarkType type, String outputDir) {
        try {
            BenchmarkSummary summary = new BenchmarkSummary(results, type);
            summary.writeToFile(outputDir + "/benchmark-summary.json");
            System.out.println("📝 Benchmark summary written to benchmark-summary.json");
        } catch (Exception e) {
            System.err.println("⚠️ Warning: Could not write benchmark summary: " + e.getMessage());
        }
    }
}