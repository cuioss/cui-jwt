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
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for MetricsDownloader using MockWebServer to test network scenarios
 */
@EnableMockWebServer
class MetricsDownloaderTest {

    @Getter
    private final MetricsModuleDispatcher moduleDispatcher = new MetricsModuleDispatcher();

    @TempDir
    Path tempDir;

    private Path downloadsDir;

    @BeforeEach void setUp() {
        downloadsDir = tempDir.resolve("downloads");
        moduleDispatcher.setCallCounter(0);
        moduleDispatcher.returnDefault();
    }

    @Test void shouldSuccessfullyDownloadMetrics(URIBuilder uriBuilder) throws IOException {
        String metricsUrl = uriBuilder.addPathSegment(MetricsModuleDispatcher.LOCAL_PATH).buildAsString();
        moduleDispatcher.returnDefault();

        MetricsDownloader downloader = new MetricsDownloader(metricsUrl, downloadsDir);
        Path downloadedFile = downloader.downloadMetrics("test-prefix");

        assertNotNull(downloadedFile, "Downloaded file path should not be null");
        assertTrue(Files.exists(downloadedFile), "Downloaded file should exist");
        assertTrue(downloadedFile.getFileName().toString().startsWith("test-prefix-"),
                "Filename should start with prefix");
        assertTrue(downloadedFile.getFileName().toString().endsWith(".txt"),
                "Filename should have .txt extension");

        String content = Files.readString(downloadedFile);
        assertFalse(content.isEmpty(), "Downloaded content should not be empty");
        assertTrue(content.contains("cui_jwt_validation"), "Content should contain JWT validation metrics");

        moduleDispatcher.assertCallsAnswered(1);
    }

    @Test void shouldHandleInternalServerError(URIBuilder uriBuilder) {
        String metricsUrl = uriBuilder.addPathSegment(MetricsModuleDispatcher.LOCAL_PATH).buildAsString();
        moduleDispatcher.returnInternalServerError();

        MetricsDownloader downloader = new MetricsDownloader(metricsUrl, downloadsDir);

        IOException exception = assertThrows(IOException.class, () ->
                downloader.downloadMetrics("error-test")
        );

        assertTrue(exception.getMessage().contains("500") ||
                exception.getMessage().toLowerCase().contains("server error"),
                "Should indicate server error, but was: " + exception.getMessage());

        moduleDispatcher.assertCallsAnswered(1);
    }

    @Test void shouldHandleServiceUnavailable(URIBuilder uriBuilder) {
        String metricsUrl = uriBuilder.addPathSegment(MetricsModuleDispatcher.LOCAL_PATH).buildAsString();
        moduleDispatcher.returnServiceUnavailable();

        MetricsDownloader downloader = new MetricsDownloader(metricsUrl, downloadsDir);

        IOException exception = assertThrows(IOException.class, () ->
                downloader.downloadMetrics("unavailable-test")
        );

        assertTrue(exception.getMessage().contains("503") ||
                exception.getMessage().toLowerCase().contains("service unavailable"),
                "Should indicate service unavailable, but was: " + exception.getMessage());

        moduleDispatcher.assertCallsAnswered(1);
    }

    @Test void shouldHandleInvalidContent(URIBuilder uriBuilder) throws IOException {
        String metricsUrl = uriBuilder.addPathSegment(MetricsModuleDispatcher.LOCAL_PATH).buildAsString();
        moduleDispatcher.returnInvalidContent();

        MetricsDownloader downloader = new MetricsDownloader(metricsUrl, downloadsDir);
        Path downloadedFile = downloader.downloadMetrics("invalid-test");

        assertNotNull(downloadedFile, "Downloaded file path should not be null");
        assertTrue(Files.exists(downloadedFile), "Downloaded file should exist");

        String content = Files.readString(downloadedFile);
        assertEquals("This is not valid Prometheus metrics format", content.trim(),
                "Should contain invalid content");

        moduleDispatcher.assertCallsAnswered(1);
    }

    @Test void shouldHandleEmptyMetrics(URIBuilder uriBuilder) throws IOException {
        String metricsUrl = uriBuilder.addPathSegment(MetricsModuleDispatcher.LOCAL_PATH).buildAsString();
        moduleDispatcher.returnEmptyMetrics();

        MetricsDownloader downloader = new MetricsDownloader(metricsUrl, downloadsDir);
        Path downloadedFile = downloader.downloadMetrics("empty-test");

        assertNotNull(downloadedFile, "Downloaded file path should not be null");
        assertTrue(Files.exists(downloadedFile), "Downloaded file should exist");

        String content = Files.readString(downloadedFile);
        assertEquals("# EOF", content.trim(), "Should contain empty metrics marker");

        moduleDispatcher.assertCallsAnswered(1);
    }

    @Test void shouldHandleCustomResponse(URIBuilder uriBuilder) throws IOException {
        String metricsUrl = uriBuilder.addPathSegment(MetricsModuleDispatcher.LOCAL_PATH).buildAsString();
        String customMetrics = """
                # TYPE custom_metric counter
                # HELP custom_metric Custom test metric
                custom_metric_total{label="test"} 42.0
                # EOF
                """;
        moduleDispatcher.setCustomResponse(customMetrics);

        MetricsDownloader downloader = new MetricsDownloader(metricsUrl, downloadsDir);
        Path downloadedFile = downloader.downloadMetrics("custom-test");

        assertTrue(Files.exists(downloadedFile), "Downloaded file should exist");

        String content = Files.readString(downloadedFile);
        assertTrue(content.contains("custom_metric_total{label=\"test\"} 42.0"),
                "Should contain custom metrics");

        moduleDispatcher.assertCallsAnswered(1);
    }

    @Test void shouldCreateDownloadDirectoryIfNotExists(URIBuilder uriBuilder) throws IOException {
        Path nonExistentDir = tempDir.resolve("new-dir/sub-dir");
        assertFalse(Files.exists(nonExistentDir), "Directory should not exist initially");

        String metricsUrl = uriBuilder.addPathSegment(MetricsModuleDispatcher.LOCAL_PATH).buildAsString();
        moduleDispatcher.returnDefault();

        MetricsDownloader downloader = new MetricsDownloader(metricsUrl, nonExistentDir);

        assertTrue(Files.exists(nonExistentDir), "Directory should be created during construction");

        Path downloadedFile = downloader.downloadMetrics("dir-test");
        assertTrue(Files.exists(downloadedFile), "Download should succeed in created directory");

        moduleDispatcher.assertCallsAnswered(1);
    }

    @Test void shouldGenerateCorrectFilename(URIBuilder uriBuilder) {
        String metricsUrl = uriBuilder.addPathSegment(MetricsModuleDispatcher.LOCAL_PATH).buildAsString();
        MetricsDownloader downloader = new MetricsDownloader(metricsUrl, downloadsDir);

        String filename1 = downloader.generateTimestampedFileName("my-prefix");
        String filename2 = downloader.generateTimestampedFileName("another-prefix");

        assertTrue(filename1.matches("my-prefix-\\d{8}_\\d{6}_\\d{3}\\.txt"),
                "Filename should match pattern: prefix-YYYYMMDD_HHMMSS_SSS.txt, but was: " + filename1);

        assertTrue(filename2.matches("another-prefix-\\d{8}_\\d{6}_\\d{3}\\.txt"),
                "Filename should match pattern: prefix-YYYYMMDD_HHMMSS_SSS.txt, but was: " + filename2);

        assertNotEquals(filename1, filename2, "Different prefixes should generate different filenames");
    }

    @Test void shouldHandleEmptyPrefix(URIBuilder uriBuilder) {
        String metricsUrl = uriBuilder.addPathSegment(MetricsModuleDispatcher.LOCAL_PATH).buildAsString();
        MetricsDownloader downloader = new MetricsDownloader(metricsUrl, downloadsDir);

        String filename = downloader.generateTimestampedFileName("");
        assertTrue(filename.matches("\\d{8}_\\d{6}_\\d{3}\\.txt"),
                "Empty prefix should generate filename with just timestamp, but was: " + filename);
    }

    @Test void shouldHandleNullPrefix(URIBuilder uriBuilder) {
        String metricsUrl = uriBuilder.addPathSegment(MetricsModuleDispatcher.LOCAL_PATH).buildAsString();
        MetricsDownloader downloader = new MetricsDownloader(metricsUrl, downloadsDir);

        String filename = downloader.generateTimestampedFileName(null);
        assertTrue(filename.matches("\\d{8}_\\d{6}_\\d{3}\\.txt"),
                "Null prefix should generate filename with just timestamp, but was: " + filename);
    }

    @Test void shouldGenerateUniqueFilenames(URIBuilder uriBuilder) {
        String metricsUrl = uriBuilder.addPathSegment(MetricsModuleDispatcher.LOCAL_PATH).buildAsString();
        MetricsDownloader downloader = new MetricsDownloader(metricsUrl, downloadsDir);

        AtomicReference<String> filename1 = new AtomicReference<>();
        AtomicReference<String> filename2 = new AtomicReference<>();

        // Generate first filename
        filename1.set(downloader.generateTimestampedFileName("test"));

        // Wait until a different timestamp is guaranteed (at least 1ms)
        Awaitility.await()
                .atMost(Duration.ofMillis(100))
                .pollDelay(Duration.ofMillis(1))
                .until(() -> {
                    String newFilename = downloader.generateTimestampedFileName("test");
                    if (!newFilename.equals(filename1.get())) {
                        filename2.set(newFilename);
                        return true;
                    }
                    return false;
                });

        assertNotEquals(filename1.get(), filename2.get(), "Should generate unique filenames even with same prefix");
    }

    @Test void shouldHandleConnectionRefused() {
        // Use a definitely unreachable URL
        MetricsDownloader downloader = new MetricsDownloader("http://127.0.0.1:1", downloadsDir);

        IOException exception = assertThrows(IOException.class, () ->
                downloader.downloadMetrics("connection-test")
        );

        assertNotNull(exception, "Should throw IOException for connection refused");
        // The exception itself is proof that connection failed
    }
}