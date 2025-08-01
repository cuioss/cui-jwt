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

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
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
 * @author Generated
 * @since 1.0
 */
public class BenchmarkContextManager {

    private static final CuiLogger LOGGER = new CuiLogger(BenchmarkContextManager.class);
    
    // Pattern to extract benchmark patterns from JMH arguments
    private static final Pattern BENCHMARK_PATTERN = Pattern.compile(".*\\.([A-Z][a-zA-Z]+)(?:Benchmark)?(?:\\.|$)");
    private static final Pattern JMH_ITERATION_PATTERN = Pattern.compile("-Djmh\\.iterations=(\\d+)");
    private static final Pattern JMH_THREADS_PATTERN = Pattern.compile("-Djmh\\.threads=(\\d+)");
    
    // Cache for benchmark context to avoid repeated expensive parsing
    private static volatile String cachedBenchmarkContext = null;
    private static final Object contextLock = new Object();
    
    // Thread-safe global counter for legacy directory numbering (deprecated)
    private static final AtomicInteger globalDirectoryCounter = new AtomicInteger(0);
    
    // Allow explicit benchmark context setting for better control
    private static volatile String explicitBenchmarkContext = null;
    
    /**
     * Gets the current benchmark context directory for metrics storage.
     * 
     * The directory format is: target/metrics-download/{number}-{context}/
     * Where:
     * - number: Sequential number starting from 1
     * - context: Derived benchmark context (e.g., "jwt-validation-i2-t10")
     * 
     * @return File pointing to the context-specific metrics directory
     */
    public static File getBenchmarkMetricsDirectory() {
        String context = getBenchmarkContext();
        
        // Use global counter for all benchmarks
        int directoryNumber = globalDirectoryCounter.incrementAndGet();
        String dirName = directoryNumber + "-" + context;
        File metricsDir = new File("target/metrics-download/" + dirName);
        
        // Create the directory
        if (!metricsDir.exists() && !metricsDir.mkdirs()) {
            LOGGER.warn("Failed to create metrics directory: {}, falling back to default", metricsDir.getAbsolutePath());
            // Fallback to default directory
            metricsDir = new File("target/metrics-download");
            metricsDir.mkdirs();
        }
        
        LOGGER.info("Using benchmark metrics directory: {}", metricsDir.getAbsolutePath());
        return metricsDir;
    }
    
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
        
        // Check if explicit context was set
        if (explicitBenchmarkContext != null) {
            String benchmarkType = extractBenchmarkTypeFromName(explicitBenchmarkContext);
            context.append(benchmarkType);
        } else {
            // Try to extract benchmark type from JVM arguments
            String benchmarkType = extractBenchmarkType();
            if (benchmarkType != null) {
                context.append(benchmarkType);
            } else {
                // Fallback to generic context
                context.append("benchmark");
            }
        }
        
        // Add JMH configuration if non-default
        String jmhConfig = extractJmhConfiguration();
        if (!jmhConfig.isEmpty()) {
            context.append("-").append(jmhConfig);
        }
        
        // Add timestamp for uniqueness
        String timestamp = Instant.now().toString().substring(11, 19).replace(":", "");
        context.append("-").append(timestamp);
        
        return context.toString();
    }
    
    /**
     * Extract benchmark type from a benchmark name or class.method string
     */
    private static String extractBenchmarkTypeFromName(String benchmarkName) {
        if (benchmarkName == null || benchmarkName.isEmpty()) {
            return "benchmark";
        }
        
        // Handle simple strings like "jwt-validation" by returning them as-is
        if (benchmarkName.contains("-") && !benchmarkName.contains(".")) {
            return benchmarkName.toLowerCase();
        }
        
        // Handle full class.method names
        if (benchmarkName.contains(".")) {
            String className = benchmarkName.substring(0, benchmarkName.lastIndexOf('.'));
            if (className.contains(".")) {
                className = className.substring(className.lastIndexOf('.') + 1);
            }
            benchmarkName = className;
        }
        
        // Extract benchmark type based on patterns
        String lowerName = benchmarkName.toLowerCase();
        if (lowerName.equals("jwtvalidation") || lowerName.contains("jwtvalidation") || 
            lowerName.contains("validatejwt") || lowerName.contains("testjwtvalidation") || 
            lowerName.contains("validateaccess") || lowerName.contains("validateid")) {
            return "jwt-validation";
        } else if (lowerName.equals("jwtecho") || lowerName.contains("jwtecho") || 
                   (lowerName.contains("echo") && lowerName.contains("jwt"))) {
            return "jwt-echo";
        } else if (lowerName.equals("jwthealth") || lowerName.contains("jwthealth") || 
                   (lowerName.contains("health") && lowerName.contains("jwt"))) {
            return "jwt-health";
        } else if (lowerName.contains("jwt")) {
            // If it just contains "jwt" without specific type, keep it simple
            return "jwt";
        } else {
            // Remove "Benchmark" suffix if present and convert to lowercase
            String cleaned = benchmarkName.replaceAll("(?i)benchmark", "").toLowerCase();
            return cleaned.isEmpty() ? "benchmark" : cleaned;
        }
    }
    
    private static String extractBenchmarkType() {
        try {
            RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
            List<String> jvmArgs = runtimeBean.getInputArguments();
            
            // Look for benchmark runner class or specific benchmark patterns
            for (String arg : jvmArgs) {
                if (arg.contains("benchmark") || arg.contains("Benchmark")) {
                    Matcher matcher = BENCHMARK_PATTERN.matcher(arg);
                    if (matcher.find()) {
                        String type = matcher.group(1);
                        return type.toLowerCase().replace("benchmark", "");
                    }
                }
            }
            
            // Try to extract from classpath or main class
            String classPath = System.getProperty("java.class.path", "");
            if (classPath.contains("JwtValidation")) {
                return "jwt-validation";
            } else if (classPath.contains("JwtEcho")) {
                return "echo";
            } else if (classPath.contains("JwtHealth")) {
                return "health";
            }
            
            // Look at current thread stack for benchmark classes
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            for (StackTraceElement element : stack) {
                String className = element.getClassName();
                if (className.contains("Benchmark")) {
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
                }
            }
            
        } catch (Exception e) {
            LOGGER.debug("Failed to extract benchmark type from JVM arguments", e);
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
            if (iterations != null && !iterations.equals("2")) {
                config.append("i").append(iterations);
            }
            
            if (threads != null && !threads.equals("10")) {
                if (config.length() > 0) config.append("-");
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
        if (context.contains("-") && context.matches(".*-\\d{6}$")) {
            // Remove timestamp suffix (6 digits)
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