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
package de.cuioss.benchmarking.common.output;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Manages the output directory structure for benchmark results.
 * Provides clear separation between deployable content (gh-pages-ready)
 * and non-deployable raw data (history, prometheus, wrk).
 */
public class OutputDirectoryStructure {
    private final Path benchmarkResultsDir;  // Root benchmark-results directory
    private final Path deploymentDir;        // gh-pages-ready directory
    private final Path htmlDir;              // gh-pages-ready (for HTML files)
    private final Path dataDir;              // gh-pages-ready/data
    private final Path badgesDir;            // gh-pages-ready/badges
    private final Path apiDir;               // gh-pages-ready/api

    // Non-deployed directories (in benchmark-results root)
    private final Path historyDir;           // benchmark-results/history
    private final Path prometheusRawDir;     // benchmark-results/prometheus
    private final Path wrkDir;               // benchmark-results/wrk (WRK module only)

    /**
     * Creates a new output directory structure.
     *
     * @param benchmarkResultsDir the root benchmark-results directory
     * @throws NullPointerException if benchmarkResultsDir is null
     */
    public OutputDirectoryStructure(Path benchmarkResultsDir) {
        this.benchmarkResultsDir = Objects.requireNonNull(benchmarkResultsDir,
                "benchmarkResultsDir must not be null");
        this.deploymentDir = benchmarkResultsDir.resolve("gh-pages-ready");
        this.htmlDir = deploymentDir;
        this.dataDir = deploymentDir.resolve("data");
        this.badgesDir = deploymentDir.resolve("badges");
        this.apiDir = deploymentDir.resolve("api");

        // Non-deployed directories stay in root
        this.historyDir = benchmarkResultsDir.resolve("history");
        this.prometheusRawDir = benchmarkResultsDir.resolve("prometheus");
        this.wrkDir = benchmarkResultsDir.resolve("wrk");
    }

    /**
     * Ensures deployment directories exist, creating them if necessary.
     * Non-deployed directories (history, prometheus, wrk) are created only when accessed.
     *
     * @throws IOException if directory creation fails
     */
    public void ensureDirectories() throws IOException {
        // Create deployment directories (always needed)
        Files.createDirectories(deploymentDir);
        Files.createDirectories(dataDir);
        Files.createDirectories(badgesDir);
        Files.createDirectories(apiDir);

        // Non-deployed directories are created on-demand by individual getters
        // to avoid creating unused directories in modules that don't need them
    }

    /**
     * Gets the root benchmark results directory.
     *
     * @return the benchmark results directory path
     */
    public Path getBenchmarkResultsDir() {
        return benchmarkResultsDir;
    }

    /**
     * Gets the deployment directory (gh-pages-ready).
     * All content in this directory should be deployable to GitHub Pages.
     *
     * @return the deployment directory path
     */
    public Path getDeploymentDir() {
        return deploymentDir;
    }

    /**
     * Gets the HTML directory (same as deployment directory).
     * HTML files should be written directly to this directory.
     *
     * @return the HTML directory path
     */
    public Path getHtmlDir() {
        return htmlDir;
    }

    /**
     * Gets the data directory (gh-pages-ready/data).
     * JSON data files should be written to this directory.
     *
     * @return the data directory path
     */
    public Path getDataDir() {
        return dataDir;
    }

    /**
     * Gets the badges directory (gh-pages-ready/badges).
     * Badge JSON files should be written to this directory.
     *
     * @return the badges directory path
     */
    public Path getBadgesDir() {
        return badgesDir;
    }

    /**
     * Gets the API directory (gh-pages-ready/api).
     * API endpoint JSON files should be written to this directory.
     *
     * @return the API directory path
     */
    public Path getApiDir() {
        return apiDir;
    }

    /**
     * Gets the history directory (benchmark-results/history).
     * Historical archive data should be stored in this directory.
     * This directory is NOT deployed to GitHub Pages.
     * Creates the directory if it doesn't exist.
     *
     * @return the history directory path
     * @throws IOException if directory creation fails
     */
    public Path getHistoryDir() throws IOException {
        Files.createDirectories(historyDir);
        return historyDir;
    }

    /**
     * Gets the raw Prometheus metrics directory (benchmark-results/prometheus).
     * Raw Prometheus data should be stored in this directory.
     * This directory is NOT deployed to GitHub Pages.
     * Creates the directory if it doesn't exist.
     *
     * @return the raw Prometheus directory path
     * @throws IOException if directory creation fails
     */
    public Path getPrometheusRawDir() throws IOException {
        Files.createDirectories(prometheusRawDir);
        return prometheusRawDir;
    }

    /**
     * Gets the WRK directory (benchmark-results/wrk).
     * WRK raw results should be stored in this directory.
     * This directory is NOT deployed to GitHub Pages.
     * Creates the directory if it doesn't exist.
     *
     * @return the WRK directory path
     * @throws IOException if directory creation fails
     */
    public Path getWrkDir() throws IOException {
        Files.createDirectories(wrkDir);
        return wrkDir;
    }

    /**
     * Checks if the deployment directory exists.
     *
     * @return true if the deployment directory exists, false otherwise
     */
    public boolean isDeploymentDirectoryExists() {
        return Files.exists(deploymentDir);
    }

    /**
     * Cleans the deployment directory by deleting it and recreating it.
     * This ensures a fresh start for report generation.
     *
     * @throws IOException if deletion or creation fails
     */
    public void cleanDeploymentDirectory() throws IOException {
        if (Files.exists(deploymentDir)) {
            deleteDirectoryRecursively(deploymentDir);
        }
        Files.createDirectories(deploymentDir);
        Files.createDirectories(dataDir);
        Files.createDirectories(badgesDir);
        Files.createDirectories(apiDir);
    }

    /**
     * Recursively deletes a directory and all its contents.
     *
     * @param path the directory to delete
     * @throws IOException if deletion fails
     */
    private void deleteDirectoryRecursively(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                    .sorted((a, b) -> b.compareTo(a)) // reverse order for deletion
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to delete: " + p, e);
                        }
                    });
        }
    }

    @Override public String toString() {
        return "OutputDirectoryStructure{" +
                "benchmarkResultsDir=" + benchmarkResultsDir +
                ", deploymentDir=" + deploymentDir +
                ", dataDir=" + dataDir +
                ", badgesDir=" + badgesDir +
                ", apiDir=" + apiDir +
                ", historyDir=" + historyDir +
                ", prometheusRawDir=" + prometheusRawDir +
                ", wrkDir=" + wrkDir +
                '}';
    }
}