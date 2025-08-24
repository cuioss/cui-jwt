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
package de.cuioss.benchmarking.common;

import org.apache.commons.io.output.TeeOutputStream;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.*;

/**
 * Centralized logging configuration for JMH benchmarks.
 * Sets up logging to write to both console and a timestamped file in benchmark-results.
 * 
 * <p>This class provides:
 * <ul>
 *   <li>Dual output to console and file via TeeOutputStream</li>
 *   <li>Capture of all System.out and System.err output</li>
 *   <li>Timestamped log files in benchmark results directory</li>
 *   <li>Configurable java.util.logging levels</li>
 * </ul>
 * 
 * @since 1.0
 */
@SuppressWarnings("java:S106") // System.out and System.err usage is intentional for logging setup
public final class BenchmarkLoggingSetup {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    // Keep references to original streams
    private static final PrintStream ORIGINAL_OUT = System.out;
    private static final PrintStream ORIGINAL_ERR = System.err;

    private BenchmarkLoggingSetup() {
        // Utility class
    }

    /**
     * Configures logging to write to both console and a timestamped file in benchmark-results.
     * Also redirects System.out and System.err to capture all console output.
     *
     * @param benchmarkResultsDir the directory where benchmark results are stored
     */
    public static void configureLogging(String benchmarkResultsDir) {
        try {
            // Ensure directory exists
            Path resultsPath = Path.of(benchmarkResultsDir);
            Files.createDirectories(resultsPath);

            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            String logFileName = "benchmark-run_%s.log".formatted(timestamp);
            Path logFile = resultsPath.resolve(logFileName);

            FileOutputStream fileOut = new FileOutputStream(logFile.toFile(), true);

            TeeOutputStream teeOut = new TeeOutputStream(ORIGINAL_OUT, fileOut);
            PrintStream newOut = new PrintStream(teeOut, true); // auto-flush enabled

            TeeOutputStream teeErr = new TeeOutputStream(ORIGINAL_ERR, fileOut);
            PrintStream newErr = new PrintStream(teeErr, true); // auto-flush enabled

            // Redirect System.out and System.err
            System.setOut(newOut);
            System.setErr(newErr);

            // Configure java.util.logging
            configureJavaUtilLogging(resultsPath, timestamp);

            // Log configuration success
            System.out.println("Benchmark logging configured - writing to: " + logFile);
            System.out.println("All console output (System.out/err and JMH) will be captured to both console and file");

        } catch (IOException e) {
            // System.err is appropriate here as logging infrastructure may not be set up yet
            System.err.println("Failed to configure file logging: " + e.getMessage());
            System.err.println("Continuing with console-only logging");
        }
    }

    /**
     * Configures logging with custom log level and package filtering.
     * 
     * @param benchmarkResultsDir the directory where benchmark results are stored
     * @param logLevel the desired log level (e.g., Level.INFO, Level.DEBUG)
     * @param packageFilter optional package name to enable detailed logging for
     */
    public static void configureLogging(String benchmarkResultsDir, Level logLevel, String packageFilter) {
        configureLogging(benchmarkResultsDir);

        // Apply custom log level
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(logLevel);

        // Enable detailed logging for specific package
        if (packageFilter != null && !packageFilter.isEmpty()) {
            Logger.getLogger(packageFilter).setLevel(Level.ALL);
        }
    }

    private static void configureJavaUtilLogging(Path resultsPath, String timestamp) throws IOException {
        Logger rootLogger = Logger.getLogger("");

        Handler[] handlers = rootLogger.getHandlers();
        for (Handler handler : handlers) {
            rootLogger.removeHandler(handler);
        }

        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.INFO);
        consoleHandler.setFormatter(new SimpleFormatter());
        rootLogger.addHandler(consoleHandler);

        String logFileName = "benchmark-jul_%s.log".formatted(timestamp);
        Path julLogFile = resultsPath.resolve(logFileName);
        FileHandler fileHandler = new FileHandler(julLogFile.toString(), true);
        fileHandler.setLevel(Level.ALL);
        fileHandler.setFormatter(new SimpleFormatter());
        rootLogger.addHandler(fileHandler);

        rootLogger.setLevel(Level.INFO);

        // Configure de.cuioss packages
        configurePackageLogging("de", Level.INFO);
        configurePackageLogging("de.cuioss", Level.INFO);
        configurePackageLogging("de.cuioss.benchmarking", Level.INFO);

        // Disable verbose JMH internal logging
        configurePackageLogging("org.openjdk.jmh", Level.WARNING);
    }

    private static void configurePackageLogging(String packageName, Level level) {
        Logger.getLogger(packageName).setLevel(level);
    }

    /**
     * Resets logging to original console-only output.
     * Useful for cleanup after benchmarks complete.
     */
    public static void resetLogging() {
        System.setOut(ORIGINAL_OUT);
        System.setErr(ORIGINAL_ERR);

        // Reset java.util.logging
        Logger rootLogger = Logger.getLogger("");
        Handler[] handlers = rootLogger.getHandlers();
        for (Handler handler : handlers) {
            handler.close();
            rootLogger.removeHandler(handler);
        }

        // Add back default console handler
        ConsoleHandler consoleHandler = new ConsoleHandler();
        rootLogger.addHandler(consoleHandler);
        rootLogger.setLevel(Level.INFO);
    }
}