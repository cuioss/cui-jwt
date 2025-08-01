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
 * Tests for BenchmarkContextManager
 */
class BenchmarkContextManagerTest {

    @BeforeEach
    void setUp() {
        // Reset context before each test
        BenchmarkContextManager.resetContext();
    }

    @AfterEach
    void tearDown() {
        // Clean up after tests
        BenchmarkContextManager.resetContext();
    }

    @Test
    void shouldDeriveReasonableBenchmarkContext() {
        // When - get benchmark context
        String context = BenchmarkContextManager.getBenchmarkContext();

        // Then - should be non-empty and contain reasonable values
        assertNotNull(context, "Benchmark context should not be null");
        assertFalse(context.isEmpty(), "Benchmark context should not be empty");
        
        // Should contain some recognizable benchmark-related content
        assertTrue(context.contains("-"), "Context should contain timestamp separator");
        
        System.out.println("Derived benchmark context: " + context);
    }

    @Test
    void shouldCacheBenchmarkContext() {
        // When - get context multiple times
        String context1 = BenchmarkContextManager.getBenchmarkContext();
        String context2 = BenchmarkContextManager.getBenchmarkContext();

        // Then - should return same cached value
        assertEquals(context1, context2, "Context should be cached and consistent");
    }

    @Test
    void shouldCreateMetricsDirectory() {
        // This test is for the old directory-based approach
        // Skip it since we now use files instead of directories
    }

    @Test
    void shouldCreateNumberedDirectories() {
        // This test is for the old directory-based approach
        // Skip it since we now use files instead of directories
    }

    @Test
    void shouldGenerateValidMetricsFilename() {
        // When - get metrics filename
        String filename = BenchmarkContextManager.getMetricsFilename();

        // Then - should be valid filename format
        assertNotNull(filename, "Filename should not be null");
        assertTrue(filename.startsWith("quarkus-metrics-"), "Should start with quarkus-metrics-");
        assertTrue(filename.endsWith(".txt"), "Should end with .txt");
        assertTrue(filename.contains("-"), "Should contain timestamp separators");
        
        // Should not contain invalid filename characters
        assertFalse(filename.contains(":"), "Should not contain colons (replaced in timestamp)");
        
        System.out.println("Generated filename: " + filename);
    }

    @Test
    void shouldResetContextProperly() throws InterruptedException {
        // Given - context has been derived
        String originalContext = BenchmarkContextManager.getBenchmarkContext();

        // When - reset context (with small delay to ensure timestamp difference)
        Thread.sleep(1100); // Wait for timestamp to change (timestamps are second-based)
        BenchmarkContextManager.resetContext();
        
        // Then - new context should be different (due to new timestamp)
        String newContext = BenchmarkContextManager.getBenchmarkContext();
        
        // Context will be different due to timestamp
        assertNotEquals(originalContext, newContext, "Context should be reset and regenerated");
        
        System.out.println("Original context: " + originalContext);
        System.out.println("New context: " + newContext);
    }
    
    @Test
    void shouldCreateDirectoriesWithCorrectNamingPattern() {
        // This test is for the old directory-based approach
        // Skip it since we now use files instead of directories
        // The actual file-based tests are in BenchmarkContextManagerFileTest
    }
    
    @Test
    void shouldHandleExplicitBenchmarkNamesCorrectly() {
        // This test is for the old directory-based approach
        // Skip it since we now use files instead of directories
        // The actual file-based tests are in BenchmarkContextManagerFileTest
    }
}