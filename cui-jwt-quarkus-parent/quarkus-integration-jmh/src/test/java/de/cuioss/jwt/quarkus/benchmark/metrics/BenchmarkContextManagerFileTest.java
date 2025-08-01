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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkContextManagerFileTest {

    @BeforeEach
    void setUp() {
        BenchmarkContextManager.resetForTesting();
    }

    @AfterEach
    void tearDown() {
        BenchmarkContextManager.resetForTesting();
    }

    @Test
    @DisplayName("Should create type-based metrics files for different benchmark contexts")
    void shouldCreateTypeBasedMetricsFiles() {
        // Arrange
        BenchmarkContextManager.resetForTesting();

        // Act
        BenchmarkContextManager.setBenchmarkContext("jwt-validation");
        File file1 = BenchmarkContextManager.getMetricsFile();

        BenchmarkContextManager.setBenchmarkContext("jwt-echo");
        File file2 = BenchmarkContextManager.getMetricsFile();

        BenchmarkContextManager.setBenchmarkContext("jwt-health");
        File file3 = BenchmarkContextManager.getMetricsFile();

        // Assert
        String file1Name = file1.getName();
        String file2Name = file2.getName();
        String file3Name = file3.getName();

        assertEquals("jwt-validation-metrics.txt", file1Name,
                "Validation should be jwt-validation-metrics.txt");
        assertEquals("jwt-echo-metrics.txt", file2Name,
                "Echo should be jwt-echo-metrics.txt");
        assertEquals("jwt-health-metrics.txt", file3Name,
                "Health should be jwt-health-metrics.txt");

        BenchmarkContextManager.setBenchmarkContext("jwt-validation");
        File file4 = BenchmarkContextManager.getMetricsFile();
        String file4Name = file4.getName();

        assertEquals("jwt-validation-metrics.txt", file4Name,
                "Same type should always get the same file name");

        assertTrue(file1.getAbsolutePath().contains("target" + File.separator + "metrics-download"));
        assertTrue(file2.getAbsolutePath().contains("target" + File.separator + "metrics-download"));
        assertTrue(file3.getAbsolutePath().contains("target" + File.separator + "metrics-download"));
        assertTrue(file4.getAbsolutePath().contains("target" + File.separator + "metrics-download"));
    }

    @Test
    @DisplayName("Should handle benchmark names from actual benchmark classes")
    void shouldHandleBenchmarkNamesFromClasses() {
        // Arrange
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

        // Act & Assert
        for (int i = 0; i < benchmarkNames.length; i++) {
            BenchmarkContextManager.setBenchmarkContext(benchmarkNames[i]);
            File file = BenchmarkContextManager.getMetricsFile();

            assertEquals(expectedFileNames[i], file.getName(),
                    "Benchmark '" + benchmarkNames[i] + "' should create file '" + expectedFileNames[i] + "'");
        }
    }

    @Test
    @DisplayName("Should strip timestamp from context when creating metrics file")
    void shouldStripTimestampFromContext() {
        // Arrange
        BenchmarkContextManager.setBenchmarkContext("jwt-validation");

        String contextWithTimestamp = BenchmarkContextManager.getBenchmarkContext();
        assertTrue(contextWithTimestamp.matches("jwt-validation-\\d{6}"),
                "Context should have timestamp: " + contextWithTimestamp);

        // Act
        File file = BenchmarkContextManager.getMetricsFile();

        // Assert
        assertEquals("jwt-validation-metrics.txt", file.getName(),
                "File name should strip timestamp from context");
    }

    @Test
    @DisplayName("Should use consistent file naming across resets")
    void shouldUseConsistentFileNaming() {
        // Arrange
        BenchmarkContextManager.setBenchmarkContext("jwt-validation");
        File file1 = BenchmarkContextManager.getMetricsFile();
        assertEquals("jwt-validation-metrics.txt", file1.getName());

        BenchmarkContextManager.setBenchmarkContext("jwt-echo");
        File file2 = BenchmarkContextManager.getMetricsFile();
        assertEquals("jwt-echo-metrics.txt", file2.getName());

        // Act
        BenchmarkContextManager.resetForTesting();

        // Assert
        BenchmarkContextManager.setBenchmarkContext("jwt-health");
        File file3 = BenchmarkContextManager.getMetricsFile();
        assertEquals("jwt-health-metrics.txt", file3.getName(),
                "Health should always get same name, regardless of reset");

        BenchmarkContextManager.setBenchmarkContext("jwt-validation");
        File file4 = BenchmarkContextManager.getMetricsFile();
        assertEquals("jwt-validation-metrics.txt", file4.getName(),
                "Validation should always get same name, regardless of reset");
    }
}