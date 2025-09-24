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

import de.cuioss.benchmarking.common.metrics.test.MetricsModuleDispatcher;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import lombok.Getter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for MetricsOrchestrator using MockWebServer to test full orchestration flow
 */
@EnableMockWebServer
class MetricsOrchestratorTest {

    @Getter
    private final MetricsModuleDispatcher moduleDispatcher = new MetricsModuleDispatcher();

    @TempDir
    Path tempDir;

    private Path downloadsDir;
    private Path targetDir;

    @BeforeEach void setUp() {
        downloadsDir = tempDir.resolve("downloads");
        targetDir = tempDir.resolve("target");
        moduleDispatcher.setCallCounter(0);
        moduleDispatcher.returnDefault();
    }

    @Test void shouldSuccessfullyOrchestateFullMetricsFlow(URIBuilder uriBuilder) throws IOException {
        String metricsUrl = uriBuilder.addPathSegment(MetricsModuleDispatcher.LOCAL_PATH).buildAsString();
        moduleDispatcher.returnDefault();

        MetricsOrchestrator orchestrator = new MetricsOrchestrator(metricsUrl, downloadsDir, targetDir);
        Map<String, Double> metrics = orchestrator.downloadAndExportMetrics("TestBenchmark", Instant.now());

        assertNotNull(metrics, "Returned metrics should not be null");
        assertFalse(metrics.isEmpty(), "Returned metrics should not be empty");

        // Verify specific JWT validation metrics are present (with labels)
        boolean hasSuccessOperations = metrics.keySet().stream()
                .anyMatch(key -> key.startsWith("cui_jwt_validation_success_operations_total"));
        assertTrue(hasSuccessOperations, "Should contain JWT validation success operations");

        boolean hasBearerTokenCount = metrics.keySet().stream()
                .anyMatch(key -> key.startsWith("cui_jwt_bearer_token_validation_seconds_count"));
        assertTrue(hasBearerTokenCount, "Should contain JWT bearer token validation count");

        boolean hasBearerTokenSum = metrics.keySet().stream()
                .anyMatch(key -> key.startsWith("cui_jwt_bearer_token_validation_seconds_sum"));
        assertTrue(hasBearerTokenSum, "Should contain JWT bearer token validation sum");

        // Verify files were created
        assertTrue(Files.exists(downloadsDir), "Downloads directory should exist");
        assertTrue(Files.list(downloadsDir).findAny().isPresent(), "Downloads directory should contain files");

        Path jsonFile = targetDir.resolve("jwt-validation-metrics.json");
        assertTrue(Files.exists(jsonFile), "JSON metrics file should be created");

        String jsonContent = Files.readString(jsonFile);
        assertTrue(jsonContent.contains("TestBenchmark"), "JSON should contain benchmark name");
        assertTrue(jsonContent.contains("cui_jwt_validation"), "JSON should contain JWT validation metrics");

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
                orchestrator.downloadAndExportMetrics("ErrorTest", Instant.now())
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
        Map<String, Double> metrics = orchestrator.downloadAndExportMetrics("EmptyTest", Instant.now());

        assertNotNull(metrics, "Returned metrics should not be null");
        assertTrue(metrics.isEmpty(), "Should return empty metrics map for empty metrics response");

        // Verify download file was still created
        assertTrue(Files.exists(downloadsDir), "Downloads directory should exist");
        assertTrue(Files.list(downloadsDir).findAny().isPresent(), "Downloads directory should contain files");

        moduleDispatcher.assertCallsAnswered(1);
    }

    @Test void shouldCreateDirectoriesWhenProcessing(URIBuilder uriBuilder) throws IOException {
        String metricsUrl = uriBuilder.addPathSegment(MetricsModuleDispatcher.LOCAL_PATH).buildAsString();
        assertFalse(Files.exists(downloadsDir), "Downloads directory should not exist initially");
        assertFalse(Files.exists(targetDir), "Target directory should not exist initially");

        MetricsOrchestrator orchestrator = new MetricsOrchestrator(metricsUrl, downloadsDir, targetDir);

        // Directories are created when processing, not during construction
        orchestrator.processQuarkusMetrics("test");

        assertTrue(Files.exists(downloadsDir), "Downloads directory should be created during processing");
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
                orchestrator.downloadAndExportMetrics("ConnectionTest", Instant.now())
        );

        assertNotNull(exception, "Should throw IOException for connection refused");
        // The exception itself is proof that connection failed
    }
}