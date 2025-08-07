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
package de.cuioss.jwt.quarkus.benchmark.logging;

import lombok.experimental.UtilityClass;
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
 * Sets up logging for JMH benchmarks to write to both console and a file in benchmark-results.
 * This is called programmatically to ensure the log file is written to the correct directory.
 */
@UtilityClass
@SuppressWarnings("java:S106") // System.out and System.err usage is intentional for logging setup
public class BenchmarkLoggingSetup {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    // Keep references to original streams
    private static final PrintStream ORIGINAL_OUT = System.out;
    private static final PrintStream ORIGINAL_ERR = System.err;

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

            // Create log file with timestamp
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            String logFileName = "benchmark-run_%s.log".formatted(timestamp);
            Path logFile = resultsPath.resolve(logFileName);

            // Create output file stream with try-with-resources
            try (FileOutputStream fileOut = new FileOutputStream(logFile.toFile(), true)) {
                // Create TeeOutputStream for System.out (writes to both console and file)
                TeeOutputStream teeOut = new TeeOutputStream(ORIGINAL_OUT, fileOut);
                PrintStream newOut = new PrintStream(teeOut, true); // auto-flush enabled

                // Create TeeOutputStream for System.err (writes to both console and file)
                TeeOutputStream teeErr = new TeeOutputStream(ORIGINAL_ERR, fileOut);
                PrintStream newErr = new PrintStream(teeErr, true); // auto-flush enabled

                // Redirect System.out and System.err
                System.setOut(newOut);
                System.setErr(newErr);

                // Configure java.util.logging as before
                configureJavaUtilLogging();

                // Log configuration success - System.out is appropriate here as we're setting up logging
                System.out.println("Benchmark logging configured - writing to: " + logFile);
                System.out.println("All console output (System.out/err and JMH) will be captured to both console and file");
            }

        } catch (IOException e) {
            // System.err is appropriate here as logging infrastructure may not be set up yet
            System.err.println("Failed to configure file logging: " + e.getMessage());
            // Keep minimal error output during logging setup
            System.err.println("Continuing with console-only logging");
        }
    }

    private static void configureJavaUtilLogging() {
        // Get root logger
        Logger rootLogger = Logger.getLogger("");

        // Remove existing handlers
        Handler[] handlers = rootLogger.getHandlers();
        for (Handler handler : handlers) {
            rootLogger.removeHandler(handler);
        }

        // Only add console handler - file logging is handled by TeeOutputStream
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.INFO);
        consoleHandler.setFormatter(new SimpleFormatter());
        rootLogger.addHandler(consoleHandler);

        // No FileHandler needed - TeeOutputStream captures everything to file

        // Set root logger level
        rootLogger.setLevel(Level.INFO);

        // Configure de.cuioss packages
        Logger.getLogger("de").setLevel(Level.INFO);
        Logger.getLogger("de.cuioss").setLevel(Level.INFO);
        Logger.getLogger("de.cuioss.jwt").setLevel(Level.INFO);
        Logger.getLogger("de.cuioss.jwt.quarkus").setLevel(Level.INFO);
        Logger.getLogger("de.cuioss.jwt.quarkus.benchmark").setLevel(Level.INFO);

        // Disable JMH internal logging
        Logger.getLogger("org.openjdk.jmh").setLevel(Level.OFF);
    }
}