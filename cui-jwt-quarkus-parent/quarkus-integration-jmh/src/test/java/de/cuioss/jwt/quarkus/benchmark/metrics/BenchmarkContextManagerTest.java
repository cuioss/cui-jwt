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

import static org.junit.jupiter.api.Assertions.*;

class BenchmarkContextManagerTest {

    @BeforeEach
    void setUp() {
        BenchmarkContextManager.resetContext();
    }

    @AfterEach
    void tearDown() {
        BenchmarkContextManager.resetContext();
    }

    @Test
    @DisplayName("Should derive reasonable benchmark context")
    void shouldDeriveReasonableBenchmarkContext() {
        // Act
        String context = BenchmarkContextManager.getBenchmarkContext();

        // Assert
        assertNotNull(context, "Benchmark context should not be null");
        assertFalse(context.isEmpty(), "Benchmark context should not be empty");
        assertTrue(context.contains("-"), "Context should contain timestamp separator");
    }

    @Test
    @DisplayName("Should cache benchmark context for consistency")
    void shouldCacheBenchmarkContext() {
        // Act
        String context1 = BenchmarkContextManager.getBenchmarkContext();
        String context2 = BenchmarkContextManager.getBenchmarkContext();

        // Assert
        assertEquals(context1, context2, "Context should be cached and consistent");
    }



    @Test
    @DisplayName("Should generate valid metrics filename with timestamp")
    void shouldGenerateValidMetricsFilename() {
        // Act
        String filename = BenchmarkContextManager.getMetricsFilename();

        // Assert
        assertNotNull(filename, "Filename should not be null");
        assertTrue(filename.startsWith("quarkus-metrics-"), "Should start with quarkus-metrics-");
        assertTrue(filename.endsWith(".txt"), "Should end with .txt");
        assertTrue(filename.contains("-"), "Should contain timestamp separators");
        assertFalse(filename.contains(":"), "Should not contain colons (replaced in timestamp)");
    }

    @Test
    @DisplayName("Should reset context properly and generate new context")
    void shouldResetContextProperly() throws InterruptedException {
        // Arrange
        String originalContext = BenchmarkContextManager.getBenchmarkContext();

        // Act
        Thread.sleep(1100);
        BenchmarkContextManager.resetContext();
        String newContext = BenchmarkContextManager.getBenchmarkContext();
        
        // Assert
        assertNotEquals(originalContext, newContext, "Context should be reset and regenerated");
    }
    
    
}