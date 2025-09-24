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

import de.cuioss.benchmarking.common.http.HttpClientFactory;
import de.cuioss.tools.logging.CuiLogger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Downloads metrics from a Quarkus /q/metrics endpoint and saves them to a download directory.
 * Handles HTTP/HTTPS connections with SSL support for local testing.
 *
 * @since 1.0
 */
public class MetricsDownloader {

    private static final CuiLogger LOGGER = new CuiLogger(MetricsDownloader.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
    private static final int HTTP_OK = 200;
    private static final int REQUEST_TIMEOUT_SECONDS = 10;

    private final String metricsURL;
    private final Path downloadsDirectory;
    private final HttpClient httpClient;

    /**
     * Creates a new metrics downloader.
     *
     * @param metricsURL URL of the metrics endpoint (e.g., https://localhost:10443)
     * @param downloadsDirectory Directory to save the downloaded metrics files
     */
    public MetricsDownloader(String metricsURL, Path downloadsDirectory) {
        this.metricsURL = metricsURL;
        this.downloadsDirectory = downloadsDirectory;
        this.httpClient = HttpClientFactory.getInsecureClient();

        try {
            Files.createDirectories(downloadsDirectory);
        } catch (IOException e) {
            LOGGER.warn("Failed to create downloads directory: {}", downloadsDirectory, e);
        }
    }

    /**
     * Downloads metrics from the endpoint and saves to a timestamped file.
     *
     * @param prefix File name prefix for the metrics file
     * @return Path to the saved metrics file
     * @throws IOException if downloading or saving fails
     */
    public Path downloadMetrics(String prefix) throws IOException {
        LOGGER.debug("Downloading metrics from: {}", metricsURL);

        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String fileName = prefix + "-" + timestamp + ".txt";
        Path outputFile = downloadsDirectory.resolve(fileName);

        String fullMetricsURL = metricsURL.endsWith("/q/metrics") ? metricsURL : metricsURL + "/q/metrics";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fullMetricsURL))
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == HTTP_OK) {
                String responseBody = response.body();
                Files.writeString(outputFile, responseBody);
                LOGGER.debug("Metrics saved to: {}", outputFile);
                return outputFile;
            } else {
                throw new IOException("Failed to download metrics. HTTP response code: " + response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted", e);
        }
    }

    /**
     * Downloads metrics and saves to a fixed file name (overwrites existing).
     *
     * @param fileName Fixed file name for the metrics file
     * @return Path to the saved metrics file
     * @throws IOException if downloading or saving fails
     */
    public Path downloadMetricsToFile(String fileName) throws IOException {
        LOGGER.debug("Downloading metrics from: {} to file: {}", metricsURL, fileName);

        Path outputFile = downloadsDirectory.resolve(fileName);
        String fullMetricsURL = metricsURL.endsWith("/q/metrics") ? metricsURL : metricsURL + "/q/metrics";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fullMetricsURL))
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == HTTP_OK) {
                String responseBody = response.body();
                Files.writeString(outputFile, responseBody);
                LOGGER.debug("Metrics saved to: {}", outputFile);
                return outputFile;
            } else {
                throw new IOException("Failed to download metrics. HTTP response code: " + response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted", e);
        }
    }

    /**
     * Downloads metrics before and after a benchmark run.
     *
     * @return Array containing [before-metrics-path, after-metrics-path]
     * @throws IOException if downloading fails
     */
    public Path[] downloadBeforeAndAfter() throws IOException {
        Path beforeMetrics = downloadMetrics("before");
        Path afterMetrics = downloadMetrics("after");
        return new Path[]{beforeMetrics, afterMetrics};
    }

    /**
     * Generates a timestamped filename for the given prefix.
     * Used for testing filename generation without actual HTTP download.
     *
     * @param prefix File name prefix (can be null or empty)
     * @return Generated filename in format "prefix-YYYYMMDD_HHMMSS.txt"
     */
    public String generateTimestampedFileName(String prefix) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        if (prefix == null || prefix.isEmpty()) {
            return timestamp + ".txt";
        }
        return prefix + "-" + timestamp + ".txt";
    }
}