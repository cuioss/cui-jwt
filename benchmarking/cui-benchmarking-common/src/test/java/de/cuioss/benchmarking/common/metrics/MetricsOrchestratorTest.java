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
package de.cuioss.benchmarking.common.metrics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.cuioss.benchmarking.common.metrics.test.MetricsModuleDispatcher;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import lombok.Getter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for MetricsOrchestrator using MockWebServer to test full orchestration flow
 */
@EnableMockWebServer
class MetricsOrchestratorTest {

    @Getter
    private final MetricsModuleDispatcher moduleDispatcher = new MetricsModuleDispatcher();

    private Path downloadsDir;
    private Path targetDir;

    @BeforeEach void setUp() throws IOException {
        // Use target directory so output can be inspected
        targetDir = Path.of("target/test-output/metrics-orchestrator");
        downloadsDir = targetDir.resolve("downloads");

        // Clean and recreate directories for each test
        if (Files.exists(targetDir)) {
            Files.walk(targetDir)
                    .sorted((a, b) -> b.compareTo(a)) // reverse order for deletion
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // Ignore deletion errors
                        }
                    });
        }
        Files.createDirectories(targetDir);
        Files.createDirectories(downloadsDir);

        moduleDispatcher.setCallCounter(0);
        moduleDispatcher.returnDefault();
    }

    @Test void shouldSuccessfullyOrchestateFullMetricsFlow(URIBuilder uriBuilder) throws IOException {
        String metricsUrl = uriBuilder.addPathSegment(MetricsModuleDispatcher.LOCAL_PATH).buildAsString();
        moduleDispatcher.returnDefault();

        MetricsOrchestrator orchestrator = new MetricsOrchestrator(metricsUrl, downloadsDir, targetDir);
        orchestrator.processQuarkusMetrics("TestBenchmark");

        // Verify download files were created
        assertTrue(Files.exists(downloadsDir), "Downloads directory should exist");
        assertTrue(Files.list(downloadsDir).findAny().isPresent(), "Downloads directory should contain files");

        // Verify quarkus-metrics.json was created in gh-pages-ready/data
        Path ghPagesDir = targetDir.resolve("gh-pages-ready").resolve("data");
        Path jsonFile = ghPagesDir.resolve("quarkus-metrics.json");
        assertTrue(Files.exists(jsonFile), "quarkus-metrics.json should be created in gh-pages-ready/data");

        String jsonContent = Files.readString(jsonFile);
        assertFalse(jsonContent.contains("TestBenchmark"), "JSON should NOT contain benchmark name in new structure");
        assertTrue(jsonContent.contains("quarkus-runtime-metrics"), "JSON should contain quarkus-runtime-metrics key");

        // Verify the new structured format with 4 main nodes
        assertTrue(jsonContent.contains("system"), "Should contain system metrics node");
        assertTrue(jsonContent.contains("http_server_requests"), "Should contain http_server_requests metrics node");
        assertTrue(jsonContent.contains("cui_jwt_validation_success_operations_total"), "Should contain JWT validation success operations node");
        assertTrue(jsonContent.contains("cui_jwt_validation_errors"), "Should contain JWT validation errors node");

        // Verify specific system metrics with new naming
        assertTrue(jsonContent.contains("cpu_usage_percent") || jsonContent.contains("cpu_cores_available"), "Should contain CPU metrics");
        assertTrue(jsonContent.contains("memory_heap_used_mb") || jsonContent.contains("memory_total_used_mb"), "Should contain meaningful memory metrics in MB");

        // Parse and verify the JSON structure
        Gson gson = new GsonBuilder().create();
        Map<String, Object> parsedJson = gson.fromJson(jsonContent, Map.class);

        // Verify top-level structure
        assertTrue(parsedJson.containsKey("quarkus-runtime-metrics"), "Should have quarkus-runtime-metrics top-level key");
        Map<String, Object> runtimeMetrics = (Map<String, Object>) parsedJson.get("quarkus-runtime-metrics");

        // Verify timestamp exists
        assertTrue(runtimeMetrics.containsKey("timestamp"), "Should have timestamp");

        // Verify all 4 main nodes exist
        assertTrue(runtimeMetrics.containsKey("system"), "Should have system node");
        assertTrue(runtimeMetrics.containsKey("http_server_requests"), "Should have http_server_requests node");
        assertTrue(runtimeMetrics.containsKey("cui_jwt_validation_success_operations_total"), "Should have success operations node");
        assertTrue(runtimeMetrics.containsKey("cui_jwt_validation_errors"), "Should have errors node");

        // Verify system metrics structure with new naming
        Map<String, Object> systemMetrics = (Map<String, Object>) runtimeMetrics.get("system");
        assertNotNull(systemMetrics, "System metrics should not be null");
        assertTrue(systemMetrics.containsKey("quarkus_cpu_usage_percent") || systemMetrics.containsKey("cpu_cores_available"),
                "System should have CPU metrics");
        assertTrue(systemMetrics.containsKey("memory_heap_used_mb") || systemMetrics.containsKey("memory_total_used_mb"),
                "System should have meaningful memory metrics");

        moduleDispatcher.assertCallsAnswered(1);
    }

    @Test void shouldProcessQuarkusMetrics(URIBuilder uriBuilder) throws IOException {
        String metricsUrl = uriBuilder.addPathSegment(MetricsModuleDispatcher.LOCAL_PATH).buildAsString();
        moduleDispatcher.returnDefault();

        MetricsOrchestrator orchestrator = new MetricsOrchestrator(metricsUrl, downloadsDir, targetDir);
        orchestrator.processQuarkusMetrics("after-benchmark");

        // Verify download file was created
        assertTrue(Files.exists(downloadsDir), "Downloads directory should exist");
        assertTrue(Files.list(downloadsDir).findAny().isPresent(), "Downloads directory should contain files");

        // Verify the downloaded file has the correct prefix
        boolean hasCorrectPrefix = Files.list(downloadsDir)
                .anyMatch(path -> path.getFileName().toString().startsWith("after-benchmark-"));
        assertTrue(hasCorrectPrefix, "Downloaded file should have 'after-benchmark-' prefix");

        moduleDispatcher.assertCallsAnswered(1);
    }

    @Test void shouldHandleDownloadErrorInOrchestration(URIBuilder uriBuilder) {
        String metricsUrl = uriBuilder.addPathSegment(MetricsModuleDispatcher.LOCAL_PATH).buildAsString();
        moduleDispatcher.returnInternalServerError();

        MetricsOrchestrator orchestrator = new MetricsOrchestrator(metricsUrl, downloadsDir, targetDir);

        IOException exception = assertThrows(IOException.class, () ->
                orchestrator.processQuarkusMetrics("ErrorTest")
        );

        assertTrue(exception.getMessage().contains("500") ||
                exception.getMessage().toLowerCase().contains("server error"),
                "Should propagate server error, but was: " + exception.getMessage());

        moduleDispatcher.assertCallsAnswered(1);
    }

    @Test void shouldHandleServiceUnavailableInOrchestration(URIBuilder uriBuilder) {
        String metricsUrl = uriBuilder.addPathSegment(MetricsModuleDispatcher.LOCAL_PATH).buildAsString();
        moduleDispatcher.returnServiceUnavailable();

        MetricsOrchestrator orchestrator = new MetricsOrchestrator(metricsUrl, downloadsDir, targetDir);

        IOException exception = assertThrows(IOException.class, () ->
                orchestrator.processQuarkusMetrics("unavailable-test")
        );

        assertTrue(exception.getMessage().contains("503") ||
                exception.getMessage().toLowerCase().contains("service unavailable"),
                "Should propagate service unavailable error, but was: " + exception.getMessage());

        moduleDispatcher.assertCallsAnswered(1);
    }

    @Test void shouldHandleEmptyMetrics(URIBuilder uriBuilder) throws IOException {
        String metricsUrl = uriBuilder.addPathSegment(MetricsModuleDispatcher.LOCAL_PATH).buildAsString();
        moduleDispatcher.returnEmptyMetrics();

        MetricsOrchestrator orchestrator = new MetricsOrchestrator(metricsUrl, downloadsDir, targetDir);
        orchestrator.processQuarkusMetrics("EmptyTest");

        // Verify download file was still created
        assertTrue(Files.exists(downloadsDir), "Downloads directory should exist");
        assertTrue(Files.list(downloadsDir).findAny().isPresent(), "Downloads directory should contain files");

        // Verify quarkus-metrics.json was created in gh-pages-ready/data (even if empty metrics)
        Path ghPagesDir = targetDir.resolve("gh-pages-ready").resolve("data");
        Path jsonFile = ghPagesDir.resolve("quarkus-metrics.json");
        assertTrue(Files.exists(jsonFile), "quarkus-metrics.json should be created even for empty metrics");

        String jsonContent = Files.readString(jsonFile);
        assertFalse(jsonContent.contains("EmptyTest"), "JSON should NOT contain benchmark name in new structure");
        assertTrue(jsonContent.contains("quarkus-runtime-metrics"), "JSON should contain quarkus-runtime-metrics key even for empty metrics");

        moduleDispatcher.assertCallsAnswered(1);
    }

    @Test void shouldCreateDirectoriesWhenProcessing(URIBuilder uriBuilder) throws IOException {
        String metricsUrl = uriBuilder.addPathSegment(MetricsModuleDispatcher.LOCAL_PATH).buildAsString();

        // Use a different directory that doesn't exist yet
        Path newTargetDir = Path.of("target/test-output/metrics-orchestrator-new");
        Path newDownloadsDir = newTargetDir.resolve("downloads");

        // Clean up if exists from previous run
        if (Files.exists(newTargetDir)) {
            Files.walk(newTargetDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // Ignore
                        }
                    });
        }

        assertFalse(Files.exists(newDownloadsDir), "Downloads directory should not exist initially");
        assertFalse(Files.exists(newTargetDir), "Target directory should not exist initially");

        MetricsOrchestrator orchestrator = new MetricsOrchestrator(metricsUrl, newDownloadsDir, newTargetDir);

        // Directories are created when processing, not during construction
        orchestrator.processQuarkusMetrics("test");

        assertTrue(Files.exists(newDownloadsDir), "Downloads directory should be created during processing");
        assertNotNull(orchestrator, "Orchestrator should be constructed successfully");
    }

    @Test void shouldHandleExistingDirectories(URIBuilder uriBuilder) throws IOException {
        String metricsUrl = uriBuilder.addPathSegment(MetricsModuleDispatcher.LOCAL_PATH).buildAsString();
        Files.createDirectories(downloadsDir);
        Files.createDirectories(targetDir);
        assertTrue(Files.exists(downloadsDir), "Downloads directory should exist before test");
        assertTrue(Files.exists(targetDir), "Target directory should exist before test");

        MetricsOrchestrator orchestrator = new MetricsOrchestrator(metricsUrl, downloadsDir, targetDir);

        assertNotNull(orchestrator, "Orchestrator should be constructed successfully");
        assertTrue(Files.exists(downloadsDir), "Downloads directory should still exist");
        assertTrue(Files.exists(targetDir), "Target directory should still exist");
        assertTrue(Files.isDirectory(downloadsDir), "Downloads path should remain a directory");
        assertTrue(Files.isDirectory(targetDir), "Target path should remain a directory");
    }

    @Test void shouldHandleMultipleSequentialCalls(URIBuilder uriBuilder) throws IOException {
        String metricsUrl = uriBuilder.addPathSegment(MetricsModuleDispatcher.LOCAL_PATH).buildAsString();
        moduleDispatcher.returnDefault();

        MetricsOrchestrator orchestrator = new MetricsOrchestrator(metricsUrl, downloadsDir, targetDir);

        // First call
        orchestrator.processQuarkusMetrics("first-call");
        // Second call
        orchestrator.processQuarkusMetrics("second-call");

        // Verify both download files were created
        assertTrue(Files.exists(downloadsDir), "Downloads directory should exist");
        long fileCount = Files.list(downloadsDir).count();
        assertEquals(2, fileCount, "Should have 2 downloaded files");

        // Verify files have different prefixes
        boolean hasFirstPrefix = Files.list(downloadsDir)
                .anyMatch(path -> path.getFileName().toString().startsWith("first-call-"));
        boolean hasSecondPrefix = Files.list(downloadsDir)
                .anyMatch(path -> path.getFileName().toString().startsWith("second-call-"));

        assertTrue(hasFirstPrefix, "Should have file with 'first-call-' prefix");
        assertTrue(hasSecondPrefix, "Should have file with 'second-call-' prefix");

        moduleDispatcher.assertCallsAnswered(2);
    }

    @Test void shouldHandleConnectionRefused() {
        // Use a definitely unreachable URL
        MetricsOrchestrator orchestrator = new MetricsOrchestrator("http://127.0.0.1:1", downloadsDir, targetDir);

        IOException exception = assertThrows(IOException.class, () ->
                orchestrator.processQuarkusMetrics("ConnectionTest")
        );

        assertNotNull(exception, "Should throw IOException for connection refused");
        // The exception itself is proof that connection failed
    }

    @Test void shouldCreateMetricsDirectlyInGhPagesReadyDirectory(URIBuilder uriBuilder) throws IOException {
        String metricsUrl = uriBuilder.addPathSegment(MetricsModuleDispatcher.LOCAL_PATH).buildAsString();
        moduleDispatcher.returnDefault();

        MetricsOrchestrator orchestrator = new MetricsOrchestrator(metricsUrl, downloadsDir, targetDir);
        orchestrator.processQuarkusMetrics("test-benchmark");

        // Verify quarkus-metrics.json was created directly in gh-pages-ready/data
        Path ghPagesDir = targetDir.resolve("gh-pages-ready").resolve("data");
        Path ghPagesMetricsFile = ghPagesDir.resolve("quarkus-metrics.json");
        assertTrue(Files.exists(ghPagesMetricsFile), "quarkus-metrics.json should be created directly in gh-pages-ready/data");

        // Verify the metrics content is correct
        String content = Files.readString(ghPagesMetricsFile);
        assertFalse(content.contains("test-benchmark"), "JSON should NOT contain benchmark name in new structure");
        assertTrue(content.contains("quarkus-runtime-metrics"), "JSON should contain quarkus-runtime-metrics key");
        assertTrue(content.contains("cui_jwt_validation"), "JSON should contain JWT validation metrics");

        // Verify NO main metrics file was created in the root target directory
        Path oldMainMetricsFile = targetDir.resolve("jwt-validation-metrics.json");
        assertFalse(Files.exists(oldMainMetricsFile), "No jwt-validation-metrics.json should be created in root target directory");

        moduleDispatcher.assertCallsAnswered(1);
    }
}