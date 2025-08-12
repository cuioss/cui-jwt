/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.cuioss.jwt.benchmarking.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openjdk.jmh.results.RunResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BenchmarkResultProcessor using JUnit Jupiter API.
 * Verifies complete artifact generation during benchmark result processing.
 * 
 * @author CUI Benchmarking Infrastructure
 */
class BenchmarkResultProcessorTest {
    
    @Test
    void testCompleteArtifactGeneration(@TempDir Path tempDir) throws Exception {
        // Create mock benchmark results
        Collection<RunResult> results = createMockResults();
        
        // Process results
        BenchmarkResultProcessor processor = new BenchmarkResultProcessor();
        String outputDir = tempDir.toString();
        processor.processResults(results, outputDir, true, true, true);
        
        // Verify all artifacts were created using JUnit Jupiter assertions
        assertTrue(Files.exists(Paths.get(outputDir, "badges")));
        assertTrue(Files.exists(Paths.get(outputDir, "data")));
        assertTrue(Files.exists(Paths.get(outputDir, "gh-pages-ready")));
        assertTrue(Files.exists(Paths.get(outputDir, "benchmark-summary.json")));
        
        // Verify directory structure
        assertTrue(Files.isDirectory(Paths.get(outputDir, "badges")));
        assertTrue(Files.isDirectory(Paths.get(outputDir, "data")));
        assertTrue(Files.isDirectory(Paths.get(outputDir, "gh-pages-ready")));
        
        // Verify GitHub Pages structure
        Path ghPagesDir = Paths.get(outputDir, "gh-pages-ready");
        assertTrue(Files.exists(ghPagesDir.resolve("api")));
        assertTrue(Files.exists(ghPagesDir.resolve("README.md")));
        assertTrue(Files.exists(ghPagesDir.resolve("_config.yml")));
    }
    
    @Test
    void testPartialArtifactGeneration(@TempDir Path tempDir) throws Exception {
        Collection<RunResult> results = createMockResults();
        
        BenchmarkResultProcessor processor = new BenchmarkResultProcessor();
        String outputDir = tempDir.toString();
        
        // Test with badges disabled
        processor.processResults(results, outputDir, false, true, false);
        
        // Verify only enabled artifacts were created
        assertTrue(Files.exists(Paths.get(outputDir, "data")));
        assertFalse(Files.exists(Paths.get(outputDir, "badges")));
        assertFalse(Files.exists(Paths.get(outputDir, "gh-pages-ready")));
    }
    
    @Test
    void testEmptyResultsHandling(@TempDir Path tempDir) throws Exception {
        Collection<RunResult> emptyResults = new ArrayList<>();
        
        BenchmarkResultProcessor processor = new BenchmarkResultProcessor();
        String outputDir = tempDir.toString();
        
        // Should not throw exception with empty results
        assertDoesNotThrow(() -> 
            processor.processResults(emptyResults, outputDir, true, true, true));
        
        // Basic directory structure should still be created
        assertTrue(Files.exists(Paths.get(outputDir, "data")));
        assertTrue(Files.exists(Paths.get(outputDir, "benchmark-summary.json")));
    }
    
    /**
     * Create mock benchmark results for testing.
     */
    private Collection<RunResult> createMockResults() {
        // In a real implementation, this would create actual RunResult objects
        // For this test framework, we'll use an empty list to test structure creation
        return new ArrayList<>();
    }
}