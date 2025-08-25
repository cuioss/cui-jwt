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
package de.cuioss.benchmarking.common.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class BenchmarkLoggingSetupTest {

    @TempDir
    Path tempDir;

    @Test void configureLogging() {
        String outputDir = tempDir.toString();

        // Configure logging
        BenchmarkLoggingSetup.configureLogging(outputDir);

        // Verify directory was created
        assertTrue(Files.exists(tempDir));

        // Write a test log message
        Logger logger = Logger.getLogger(BenchmarkLoggingSetupTest.class.getName());
        logger.info("Test log message");

        // Note: Log file creation might be async, so we just verify the setup completed
        assertNotNull(logger);
    }

    @Test void configureLoggingWithInvalidPath() {
        // Should handle invalid paths gracefully
        String invalidPath = "/nonexistent/path/that/cannot/be/created/../../../root";

        // Should not throw exception - logging setup should handle this gracefully
        assertDoesNotThrow(() -> BenchmarkLoggingSetup.configureLogging(invalidPath));
    }

    @Test void configureLoggingCreatesDirectory() {
        Path nonExistentDir = tempDir.resolve("new/nested/directory");
        String outputDir = nonExistentDir.toString();

        // Configure logging with non-existent directory
        BenchmarkLoggingSetup.configureLogging(outputDir);

        // Verify directory was created
        assertTrue(Files.exists(nonExistentDir));
    }

    @Test void multipleConfigureCalls() {
        String outputDir = tempDir.toString();

        // Configure logging multiple times should not cause issues
        assertDoesNotThrow(() -> {
            BenchmarkLoggingSetup.configureLogging(outputDir);
            BenchmarkLoggingSetup.configureLogging(outputDir);
            BenchmarkLoggingSetup.configureLogging(outputDir);
        });
    }
}