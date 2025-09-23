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
package de.cuioss.jwt.wrk.benchmark;

import de.cuioss.tools.logging.CuiLogger;

import javax.net.ssl.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Fetches Quarkus metrics from the metrics endpoint for resource utilization analysis.
 * <p>
 * This class downloads metrics in Prometheus format from the Quarkus application
 * during WRK benchmark execution to capture CPU and memory usage data.
 */
public class QuarkusMetricsFetcher {

    private static final CuiLogger LOGGER = new CuiLogger(QuarkusMetricsFetcher.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final String metricsUrl;
    private final Path outputDirectory;

    /**
     * Creates a new metrics fetcher.
     *
     * @param metricsUrl URL of the Quarkus metrics endpoint
     * @param outputDirectory Directory to save the downloaded metrics
     */
    public QuarkusMetricsFetcher(String metricsUrl, Path outputDirectory) {
        this.metricsUrl = metricsUrl;
        this.outputDirectory = outputDirectory;
    }

    /**
     * Main entry point for fetching metrics via command line.
     *
     * @param args Command line arguments:
     *             args[0] - Metrics URL (e.g., https://localhost:10443/q/metrics)
     *             args[1] - Output directory
     *             args[2] - (Optional) Metrics file prefix (default: "quarkus-metrics")
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            LOGGER.error("Usage: QuarkusMetricsFetcher <metrics-url> <output-dir> [prefix]");
            System.exit(1);
        }

        String metricsUrl = args[0];
        Path outputDir = Path.of(args[1]);
        String prefix = args.length > 2 ? args[2] : "quarkus-metrics";

        try {
            Files.createDirectories(outputDir);

            QuarkusMetricsFetcher fetcher = new QuarkusMetricsFetcher(metricsUrl, outputDir);
            Path metricsFile = fetcher.fetchMetrics(prefix);

            LOGGER.info("Metrics downloaded successfully to: " + metricsFile);
        } catch (Exception e) {
            LOGGER.error("Failed to fetch metrics from: " + metricsUrl, e);
            System.exit(1);
        }
    }

    /**
     * Fetches metrics from the Quarkus endpoint and saves them to a file.
     *
     * @param prefix File name prefix for the metrics file
     * @return Path to the saved metrics file
     * @throws IOException if fetching or saving fails
     */
    public Path fetchMetrics(String prefix) throws IOException {
        LOGGER.debug("Fetching metrics from: " + metricsUrl);

        // Create timestamp for unique file name
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String fileName = prefix + "-" + timestamp + ".txt";
        Path outputFile = outputDirectory.resolve(fileName);

        // Configure SSL to accept self-signed certificates (for local testing)
        configureTrustAllCertificates();

        // Fetch metrics
        URL url = new URL(metricsUrl + "/q/metrics");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);

        try {
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                throw new IOException("Failed to fetch metrics. HTTP response code: " + responseCode);
            }

            // Read and save metrics
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                 BufferedWriter writer = Files.newBufferedWriter(outputFile)) {

                String line;
                while ((line = reader.readLine()) != null) {
                    writer.write(line);
                    writer.newLine();
                }
            }

            LOGGER.debug("Metrics saved to: " + outputFile);
            return outputFile;

        } finally {
            connection.disconnect();
        }
    }

    /**
     * Fetches metrics before and after a benchmark run.
     *
     * @return Array containing [before-metrics-path, after-metrics-path]
     * @throws IOException if fetching fails
     */
    public Path[] fetchBeforeAndAfter() throws IOException {
        Path beforeMetrics = fetchMetrics("before");

        // Note: The actual benchmark execution happens between these calls
        // This method is meant to be called from the build process with appropriate delays

        Path afterMetrics = fetchMetrics("after");

        return new Path[]{beforeMetrics, afterMetrics};
    }

    /**
     * Configures SSL to accept all certificates (for local testing with self-signed certs).
     * WARNING: This should only be used for local testing, not in production.
     */
    private void configureTrustAllCertificates() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }

                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }
                    }
            };

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            HostnameVerifier allHostsValid = (hostname, session) -> true;
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);

        } catch (Exception e) {
            LOGGER.warn("Failed to configure SSL trust manager", e);
        }
    }
}