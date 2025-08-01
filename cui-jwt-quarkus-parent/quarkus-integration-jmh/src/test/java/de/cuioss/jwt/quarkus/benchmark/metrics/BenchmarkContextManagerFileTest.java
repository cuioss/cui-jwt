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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BenchmarkContextManager file naming functionality
 */
class BenchmarkContextManagerFileTest {

    @BeforeEach
    void setUp() {
        // Reset context before each test
        BenchmarkContextManager.resetForTesting();
    }

    @AfterEach
    void tearDown() {
        // Clean up after tests
        BenchmarkContextManager.resetForTesting();
    }

    @Test
    void shouldCreateTypeBasedMetricsFiles() {
        // Given - we simulate different benchmark contexts
        BenchmarkContextManager.resetForTesting();
        
        // When - creating files for different benchmarks
        BenchmarkContextManager.setBenchmarkContext("jwt-validation");
        File file1 = BenchmarkContextManager.getMetricsFile();
        
        BenchmarkContextManager.setBenchmarkContext("jwt-echo");
        File file2 = BenchmarkContextManager.getMetricsFile();
        
        BenchmarkContextManager.setBenchmarkContext("jwt-health");
        File file3 = BenchmarkContextManager.getMetricsFile();
        
        // Then - files should follow the type-based naming pattern
        String file1Name = file1.getName();
        String file2Name = file2.getName();
        String file3Name = file3.getName();
        
        // Should have no numbers in file names
        assertEquals("jwt-validation-metrics.txt", file1Name, 
            "Validation should be jwt-validation-metrics.txt");
        assertEquals("jwt-echo-metrics.txt", file2Name, 
            "Echo should be jwt-echo-metrics.txt");
        assertEquals("jwt-health-metrics.txt", file3Name, 
            "Health should be jwt-health-metrics.txt");
        
        // When - creating second file for same benchmark type
        BenchmarkContextManager.setBenchmarkContext("jwt-validation");
        File file4 = BenchmarkContextManager.getMetricsFile();
        String file4Name = file4.getName();
        
        // Then - should reuse the same file name
        assertEquals("jwt-validation-metrics.txt", file4Name, 
            "Same type should always get the same file name");
        
        // All files should be in target/metrics-download directory
        assertTrue(file1.getAbsolutePath().contains("target" + File.separator + "metrics-download"));
        assertTrue(file2.getAbsolutePath().contains("target" + File.separator + "metrics-download"));
        assertTrue(file3.getAbsolutePath().contains("target" + File.separator + "metrics-download"));
        assertTrue(file4.getAbsolutePath().contains("target" + File.separator + "metrics-download"));
        
        System.out.println("File 1: " + file1Name);
        System.out.println("File 2: " + file2Name);
        System.out.println("File 3: " + file3Name);
        System.out.println("File 4: " + file4Name);
    }
    
    @Test
    void shouldHandleBenchmarkNamesFromClasses() {
        // Given - benchmark names from actual benchmark classes
        String[] benchmarkNames = {
            "JwtValidation",
            "JwtEcho",
            "JwtHealth"
        };
        
        String[] expectedFileNames = {
            "jwt-validation-metrics.txt",
            "jwt-echo-metrics.txt",
            "jwt-health-metrics.txt"
        };
        
        // When/Then - test each benchmark name
        for (int i = 0; i < benchmarkNames.length; i++) {
            BenchmarkContextManager.setBenchmarkContext(benchmarkNames[i]);
            File file = BenchmarkContextManager.getMetricsFile();
            
            assertEquals(expectedFileNames[i], file.getName(),
                "Benchmark '" + benchmarkNames[i] + "' should create file '" + expectedFileNames[i] + "'");
        }
    }
    
    @Test
    void shouldStripTimestampFromContext() {
        // Given - a context with timestamp
        BenchmarkContextManager.setBenchmarkContext("jwt-validation");
        
        // Force getting context which adds timestamp
        String contextWithTimestamp = BenchmarkContextManager.getBenchmarkContext();
        assertTrue(contextWithTimestamp.matches("jwt-validation-\\d{6}"), 
            "Context should have timestamp: " + contextWithTimestamp);
        
        // When - creating a metrics file
        File file = BenchmarkContextManager.getMetricsFile();
        
        // Then - filename should not include the timestamp
        assertEquals("jwt-validation-metrics.txt", file.getName(),
            "File name should strip timestamp from context");
    }
    
    @Test
    void shouldUseConsistentFileNaming() {
        // Given - create files
        BenchmarkContextManager.setBenchmarkContext("jwt-validation");
        File file1 = BenchmarkContextManager.getMetricsFile();
        assertEquals("jwt-validation-metrics.txt", file1.getName());
        
        BenchmarkContextManager.setBenchmarkContext("jwt-echo");
        File file2 = BenchmarkContextManager.getMetricsFile();
        assertEquals("jwt-echo-metrics.txt", file2.getName());
        
        // When - reset and create another file
        BenchmarkContextManager.resetForTesting();
        
        // Then - same types should get same numbers (type-based, not sequential)
        BenchmarkContextManager.setBenchmarkContext("jwt-health");
        File file3 = BenchmarkContextManager.getMetricsFile();
        assertEquals("jwt-health-metrics.txt", file3.getName(),
            "Health should always get same name, regardless of reset");
            
        // Validation should still get 3
        BenchmarkContextManager.setBenchmarkContext("jwt-validation");
        File file4 = BenchmarkContextManager.getMetricsFile();
        assertEquals("jwt-validation-metrics.txt", file4.getName(),
            "Validation should always get same name, regardless of reset");
    }
}