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
package de.cuioss.jwt.quarkus.benchmark.metrics;

import de.cuioss.tools.logging.CuiLogger;
import lombok.experimental.UtilityClass;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages benchmark context and metrics directories.
 *
 * This class provides intelligent management of metrics storage by:
 * - Detecting the current benchmark context from JVM arguments
 * - Creating context-specific metrics files
 * - Caching context resolution to avoid repeated parsing
 * - Ensuring thread-safe file management
 *
 * @since 1.0
 */
@UtilityClass
public class BenchmarkContextManager {

    private static final CuiLogger LOGGER = new CuiLogger(BenchmarkContextManager.class);

    // Pattern to extract benchmark patterns from JMH arguments
    private static final Pattern BENCHMARK_PATTERN = Pattern.compile(".*\\.([A-Z][a-zA-Z]+)(?:Benchmark)?(?:\\.|$)");
    private static final Pattern JMH_ITERATION_PATTERN = Pattern.compile("-Djmh\\.iterations=(\\d+)");
    private static final Pattern JMH_THREADS_PATTERN = Pattern.compile("-Djmh\\.threads=(\\d+)");

    // Default JMH configuration values
    private static final String DEFAULT_ITERATIONS = "2";
    private static final String DEFAULT_THREADS = "10";

    // Timestamp format constants
    private static final int TIMESTAMP_START = 11;
    private static final int TIMESTAMP_END = 19;
    private static final int TIMESTAMP_LENGTH = 6;

    // Cache for benchmark context to avoid repeated expensive parsing
    private static volatile String cachedBenchmarkContext = null;
    private static final Object contextLock = new Object();

    // Thread-safe global counter for legacy directory numbering (deprecated)
    private static final AtomicInteger globalDirectoryCounter = new AtomicInteger(0);

    // Allow explicit benchmark context setting for better control
    private static volatile String explicitBenchmarkContext = null;

    /**
     * Derives the benchmark context from the current JVM execution.
     *
     * The context includes:
     * - Benchmark type (extracted from class names or patterns)
     * - JMH configuration (iterations, threads if different from defaults)
     * - Timestamp for uniqueness
     *
     * @return Benchmark context string suitable for directory naming
     */
    public static String getBenchmarkContext() {
        if (cachedBenchmarkContext != null) {
            return cachedBenchmarkContext;
        }

        synchronized (contextLock) {
            if (cachedBenchmarkContext != null) {
                return cachedBenchmarkContext;
            }

            cachedBenchmarkContext = deriveBenchmarkContext();
            LOGGER.info("Derived benchmark context: {}", cachedBenchmarkContext);
            return cachedBenchmarkContext;
        }
    }

    /**
     * Explicitly set the benchmark context. Useful when the benchmark name is known.
     * This will override automatic detection.
     *
     * @param benchmarkName The benchmark name or full class.method name
     */
    public static void setBenchmarkContext(String benchmarkName) {
        synchronized (contextLock) {
            explicitBenchmarkContext = benchmarkName;
            cachedBenchmarkContext = null; // Force re-derivation
            // Don't clear directoryCounters - we want to keep counting across contexts
        }
    }

    private static String deriveBenchmarkContext() {
        StringBuilder context = new StringBuilder();

        // Append benchmark type
        appendBenchmarkType(context);

        // Add JMH configuration if non-default
        appendJmhConfiguration(context);

        // Add timestamp for uniqueness
        appendTimestamp(context);

        return context.toString();
    }

    private static void appendBenchmarkType(StringBuilder context) {
        String benchmarkType;
        if (explicitBenchmarkContext != null) {
            benchmarkType = extractBenchmarkTypeFromName(explicitBenchmarkContext);
        } else {
            benchmarkType = extractBenchmarkType();
            if (benchmarkType == null) {
                benchmarkType = "benchmark";
            }
        }
        context.append(benchmarkType);
    }

    private static void appendJmhConfiguration(StringBuilder context) {
        String jmhConfig = extractJmhConfiguration();
        if (!jmhConfig.isEmpty()) {
            context.append("-").append(jmhConfig);
        }
    }

    private static void appendTimestamp(StringBuilder context) {
        String timestamp = Instant.now().toString()
                .substring(TIMESTAMP_START, TIMESTAMP_END)
                .replace(":", "");
        context.append("-").append(timestamp);
    }

    /**
     * Extract benchmark type from a benchmark name or class.method string
     */
    private static String extractBenchmarkTypeFromName(String benchmarkName) {
        if (benchmarkName == null || benchmarkName.isEmpty()) {
            return "benchmark";
        }

        // Handle simple strings like "jwt-validation" by returning them as-is
        if (isSimpleBenchmarkName(benchmarkName)) {
            return benchmarkName.toLowerCase();
        }

        // Extract class name from full qualified name
        String simplifiedName = extractSimpleClassName(benchmarkName);

        // Determine benchmark type based on patterns
        return determineBenchmarkType(simplifiedName);
    }

    private static boolean isSimpleBenchmarkName(String benchmarkName) {
        return benchmarkName.contains("-") && !benchmarkName.contains(".");
    }

    private static String extractSimpleClassName(String benchmarkName) {
        if (!benchmarkName.contains(".")) {
            return benchmarkName;
        }

        String className = benchmarkName.substring(0, benchmarkName.lastIndexOf('.'));
        if (className.contains(".")) {
            className = className.substring(className.lastIndexOf('.') + 1);
        }
        return className;
    }

    private static String determineBenchmarkType(String benchmarkName) {
        String lowerName = benchmarkName.toLowerCase();

        if (isValidationBenchmark(lowerName)) {
            return "jwt-validation";
        } else if (isEchoBenchmark(lowerName)) {
            return "jwt-echo";
        } else if (isHealthBenchmark(lowerName)) {
            return "jwt-health";
        } else if (lowerName.contains("jwt")) {
            return "jwt";
        } else {
            return cleanBenchmarkName(benchmarkName);
        }
    }

    private static boolean isValidationBenchmark(String lowerName) {
        return "jwtvalidation".equals(lowerName) || lowerName.contains("jwtvalidation") ||
                lowerName.contains("validatejwt") || lowerName.contains("testjwtvalidation") ||
                lowerName.contains("validateaccess") || lowerName.contains("validateid");
    }

    private static boolean isEchoBenchmark(String lowerName) {
        return "jwtecho".equals(lowerName) || lowerName.contains("jwtecho") ||
                (lowerName.contains("echo") && lowerName.contains("jwt"));
    }

    private static boolean isHealthBenchmark(String lowerName) {
        return "jwthealth".equals(lowerName) || lowerName.contains("jwthealth") ||
                (lowerName.contains("health") && lowerName.contains("jwt"));
    }

    private static String cleanBenchmarkName(String benchmarkName) {
        String cleaned = benchmarkName.replaceAll("(?i)benchmark", "").toLowerCase();
        return cleaned.isEmpty() ? "benchmark" : cleaned;
    }

    private static String extractBenchmarkType() {
        try {
            // Try JVM arguments first
            String typeFromArgs = extractBenchmarkTypeFromJvmArgs();
            if (typeFromArgs != null) {
                return typeFromArgs;
            }

            // Try classpath
            String typeFromClasspath = extractBenchmarkTypeFromClasspath();
            if (typeFromClasspath != null) {
                return typeFromClasspath;
            }

            // Try stack trace
            return extractBenchmarkTypeFromStackTrace();

        } catch (Exception e) {
            LOGGER.debug("Failed to extract benchmark type from JVM arguments", e);
        }

        return null;
    }

    private static String extractBenchmarkTypeFromJvmArgs() {
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        List<String> jvmArgs = runtimeBean.getInputArguments();

        for (String arg : jvmArgs) {
            if (arg.contains("benchmark") || arg.contains("Benchmark")) {
                Matcher matcher = BENCHMARK_PATTERN.matcher(arg);
                if (matcher.find()) {
                    String type = matcher.group(1);
                    return type.toLowerCase().replace("benchmark", "");
                }
            }
        }
        return null;
    }

    private static String extractBenchmarkTypeFromClasspath() {
        String classPath = System.getProperty("java.class.path", "");
        if (classPath.contains("JwtValidation")) {
            return "jwt-validation";
        } else if (classPath.contains("JwtEcho")) {
            return "echo";
        } else if (classPath.contains("JwtHealth")) {
            return "health";
        }
        return null;
    }

    private static String extractBenchmarkTypeFromStackTrace() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stack) {
            String className = element.getClassName();
            if (className.contains("Benchmark")) {
                String type = extractTypeFromBenchmarkClass(className);
                if (type != null) {
                    return type;
                }
            }
        }
        return null;
    }

    private static String extractTypeFromBenchmarkClass(String className) {
        if (className.contains("JwtValidation")) {
            return "jwt-validation";
        } else if (className.contains("JwtEcho")) {
            return "echo";
        } else if (className.contains("JwtHealth")) {
            return "health";
        } else if (className.contains("Benchmark")) {
            // Extract last part of class name
            String[] parts = className.split("\\.");
            String lastPart = parts[parts.length - 1].toLowerCase();
            return lastPart.replace("benchmark", "");
        }
        return null;
    }

    private static String extractJmhConfiguration() {
        StringBuilder config = new StringBuilder();

        try {
            RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
            List<String> jvmArgs = runtimeBean.getInputArguments();

            // Look for JMH-specific configuration
            String iterations = null;
            String threads = null;

            for (String arg : jvmArgs) {
                Matcher iterMatcher = JMH_ITERATION_PATTERN.matcher(arg);
                if (iterMatcher.find()) {
                    iterations = iterMatcher.group(1);
                }

                Matcher threadMatcher = JMH_THREADS_PATTERN.matcher(arg);
                if (threadMatcher.find()) {
                    threads = threadMatcher.group(1);
                }
            }

            // Add non-default configuration
            if (iterations != null && !DEFAULT_ITERATIONS.equals(iterations)) {
                config.append("i").append(iterations);
            }

            if (threads != null && !DEFAULT_THREADS.equals(threads)) {
                if (!config.isEmpty()) config.append("-");
                config.append("t").append(threads);
            }

        } catch (Exception e) {
            LOGGER.debug("Failed to extract JMH configuration", e);
        }

        return config.toString();
    }

    /**
     * Resets the cached benchmark context. Useful for testing or when context changes.
     */
    public static void resetContext() {
        synchronized (contextLock) {
            cachedBenchmarkContext = null;
            explicitBenchmarkContext = null;
            // Don't reset the global counter - it should persist across benchmark runs
            // globalDirectoryCounter.set(0);
        }
    }

    /**
     * Resets everything including the counter. Only for testing purposes.
     */
    public static void resetForTesting() {
        synchronized (contextLock) {
            cachedBenchmarkContext = null;
            explicitBenchmarkContext = null;
            globalDirectoryCounter.set(0);
            // Note: No persistent counter file is needed
        }
    }

    /**
     * Gets a simple metrics filename with timestamp for the current context.
     *
     * @return Filename in format: quarkus-metrics-{timestamp}.txt
     */
    public static String getMetricsFilename() {
        String timestamp = Instant.now().toString().replace(":", "-");
        return "quarkus-metrics-" + timestamp + ".txt";
    }

    /**
     * Gets a metrics file in target/metrics-download.
     * Format: {benchmarkType}-metrics.txt
     * Example: jwt-echo-metrics.txt, jwt-health-metrics.txt, jwt-validation-metrics.txt
     *
     * @return File object for the metrics file
     */
    public static File getMetricsFile() {
        String context = getBenchmarkContext();

        // Extract just the benchmark type without timestamp
        String benchmarkType = context;
        if (context.contains("-") && context.matches(".*-\\d{" + TIMESTAMP_LENGTH + "}$")) {
            // Remove timestamp suffix
            benchmarkType = context.substring(0, context.lastIndexOf('-'));
        }

        // Create filename
        String fileName = benchmarkType + "-metrics.txt";

        // Ensure directory exists
        File metricsDir = new File("target/metrics-download");
        if (!metricsDir.exists()) {
            metricsDir.mkdirs();
        }

        File metricsFile = new File(metricsDir, fileName);
        LOGGER.info("Using metrics file: {}", metricsFile.getAbsolutePath());
        return metricsFile;
    }

}