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
package de.cuioss.benchmarking.common.report;

import de.cuioss.benchmarking.common.util.JsonSerializationHelper;
import de.cuioss.tools.logging.CuiLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static de.cuioss.benchmarking.common.util.BenchmarkingLogMessages.INFO;
import static de.cuioss.benchmarking.common.util.BenchmarkingLogMessages.WARN;

/**
 * Manages historical benchmark data persistence.
 * <p>
 * This manager handles:
 * <ul>
 *   <li>Saving current benchmark runs to history with timestamp-based naming</li>
 *   <li>Maintaining a retention policy (keeping only the most recent runs)</li>
 *   <li>Organizing historical data in a queryable directory structure</li>
 * </ul>
 * <p>
 * File naming convention: {@code YYYY-MM-DD-THHMMZ-{commitSha}.json}
 * where the timestamp is in UTC and the commit SHA is truncated to 8 characters.
 */
public class HistoricalDataManager {

    private static final CuiLogger LOGGER = new CuiLogger(HistoricalDataManager.class);
    private static final int RETENTION_COUNT = 10;
    private static final String HISTORY_DIR = "history";
    private static final String JSON_EXTENSION = ".json";
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-'T'HHmm'Z'").withZone(ZoneOffset.UTC);


    /**
     * Archives the current benchmark data to the history directory.
     *
     * @param currentData the current benchmark data to archive
     * @param outputDir the base output directory
     * @param commitSha the Git commit SHA (will be truncated to 8 chars)
     * @throws IOException if writing the archive file fails
     */
    public void archiveCurrentRun(Map<String, Object> currentData, String outputDir, String commitSha)
            throws IOException {
        Path historyDir = Path.of(outputDir, HISTORY_DIR);
        Files.createDirectories(historyDir);

        String timestamp = TIMESTAMP_FORMAT.format(Instant.now());
        String truncatedSha = commitSha != null && commitSha.length() >= 8
                ? commitSha.substring(0, 8)
                : "unknown";
        String filename = "%s-%s%s".formatted(timestamp, truncatedSha, JSON_EXTENSION);

        Path archiveFile = historyDir.resolve(filename);
        String jsonContent = JsonSerializationHelper.toJson(currentData);
        Files.writeString(archiveFile, jsonContent);

        LOGGER.info(INFO.GENERATING_REPORTS.format("Archived benchmark data to " + archiveFile));
    }

    /**
     * Enforces the retention policy by keeping only the most recent files.
     *
     * @param historyDir the history directory path
     * @throws IOException if accessing or deleting files fails
     */
    public void enforceRetentionPolicy(Path historyDir) throws IOException {
        if (!Files.exists(historyDir)) {
            return;
        }

        try (Stream<Path> files = Files.list(historyDir)) {
            List<Path> jsonFiles = files
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(JSON_EXTENSION))
                    .sorted(Comparator.comparing(this::extractTimestamp).reversed())
                    .toList();

            if (jsonFiles.size() > RETENTION_COUNT) {
                List<Path> filesToDelete = jsonFiles.subList(RETENTION_COUNT, jsonFiles.size());
                for (Path file : filesToDelete) {
                    Files.delete(file);
                    LOGGER.info(INFO.GENERATING_REPORTS.format("Removed old history file: " + file.getFileName()));
                }
            }
        }
    }

    /**
     * Retrieves a sorted list of historical data files.
     *
     * @param historyDir the history directory path
     * @return sorted list of historical data files (newest first)
     * @throws IOException if reading the directory fails
     */
    public List<Path> getHistoricalFiles(Path historyDir) throws IOException {
        if (!Files.exists(historyDir)) {
            return List.of();
        }

        try (Stream<Path> files = Files.list(historyDir)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(JSON_EXTENSION))
                    .sorted(Comparator.comparing(this::extractTimestamp).reversed())
                    .toList();
        }
    }

    /**
     * Checks if historical data is available.
     *
     * @param outputDir the base output directory
     * @return true if historical data exists, false otherwise
     */
    public boolean hasHistoricalData(String outputDir) {
        Path historyDir = Path.of(outputDir, HISTORY_DIR);
        if (!Files.exists(historyDir)) {
            return false;
        }

        try (Stream<Path> files = Files.list(historyDir)) {
            return files
                    .filter(Files::isRegularFile)
                    .anyMatch(p -> p.toString().endsWith(JSON_EXTENSION));
        } catch (IOException e) {
            LOGGER.warn(WARN.ISSUE_DURING_INDEX_GENERATION.format("checking historical data"), e);
            return false;
        }
    }

    /**
     * Extracts the timestamp from a historical data filename.
     *
     * @param file the file path
     * @return the timestamp string, or empty string if extraction fails
     */
    private String extractTimestamp(Path file) {
        String filename = file.getFileName().toString();
        int dashIndex = filename.lastIndexOf('-');
        if (dashIndex > 0) {
            return filename.substring(0, dashIndex);
        }
        return "";
    }
}