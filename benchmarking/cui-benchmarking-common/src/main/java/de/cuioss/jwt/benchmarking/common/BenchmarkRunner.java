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

import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.results.RunResult;

import java.util.Collection;

/**
 * Standardized JMH benchmark runner that generates all artifacts during execution.
 * This runner integrates badge generation, report creation, and GitHub Pages preparation
 * directly into the JMH benchmark execution process.
 * 
 * @author CUI Benchmarking Infrastructure
 */
public class BenchmarkRunner {

    private static final String OUTPUT_DIR_PROPERTY = "benchmark.output.dir";
    private static final String DEFAULT_OUTPUT_DIR = "target/benchmark-results";
    private static final String GENERATE_BADGES_PROPERTY = "benchmark.generate.badges";
    private static final String GENERATE_REPORTS_PROPERTY = "benchmark.generate.reports";
    private static final String GENERATE_GITHUB_PAGES_PROPERTY = "benchmark.generate.github.pages";

    /**
     * Main entry point for benchmark execution with complete artifact generation.
     */
    public static void main(String[] args) throws Exception {
        String outputDir = System.getProperty(OUTPUT_DIR_PROPERTY, DEFAULT_OUTPUT_DIR);
        boolean generateBadges = Boolean.parseBoolean(System.getProperty(GENERATE_BADGES_PROPERTY, "true"));
        boolean generateReports = Boolean.parseBoolean(System.getProperty(GENERATE_REPORTS_PROPERTY, "true"));
        boolean generateGitHubPages = Boolean.parseBoolean(System.getProperty(GENERATE_GITHUB_PAGES_PROPERTY, "true"));

        System.out.println("🚀 Starting JMH Benchmarks with integrated artifact generation");
        System.out.println("📁 Output directory: " + outputDir);
        System.out.println("🏷️ Generate badges: " + generateBadges);
        System.out.println("📊 Generate reports: " + generateReports);
        System.out.println("📚 Generate GitHub Pages: " + generateGitHubPages);

        BenchmarkRunner runner = new BenchmarkRunner();
        runner.runBenchmarks(outputDir, generateBadges, generateReports, generateGitHubPages);
    }

    /**
     * Execute benchmarks and generate all artifacts.
     */
    public void runBenchmarks(String outputDir, boolean generateBadges, boolean generateReports, boolean generateGitHubPages) 
            throws RunnerException {
        
        // Configure JMH options
        Options options = new OptionsBuilder()
            .include(BenchmarkOptionsHelper.getInclude())
            .forks(BenchmarkOptionsHelper.getForks())
            .warmupIterations(BenchmarkOptionsHelper.getWarmupIterations())
            .measurementIterations(BenchmarkOptionsHelper.getMeasurementIterations())
            .resultFormat(ResultFormatType.JSON)
            .result(outputDir + "/raw-result.json")
            .build();

        System.out.println("⏱️ Executing JMH benchmarks...");
        Collection<RunResult> results = new Runner(options).run();
        System.out.println("✅ JMH execution completed with " + results.size() + " benchmark results");

        // Process results to generate all artifacts
        BenchmarkResultProcessor processor = new BenchmarkResultProcessor();
        processor.processResults(results, outputDir, generateBadges, generateReports, generateGitHubPages);

        System.out.println("🎯 Benchmark execution and artifact generation completed successfully");
    }
}