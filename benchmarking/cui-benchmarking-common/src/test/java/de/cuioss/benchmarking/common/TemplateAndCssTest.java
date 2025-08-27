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

import de.cuioss.benchmarking.common.config.BenchmarkType;
import de.cuioss.benchmarking.common.report.ReportGenerator;
import de.cuioss.benchmarking.common.runner.BenchmarkResultProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for template loading and CSS linking behavior in report generation.
 * <p>
 * These tests ensure that:
 * <ul>
 *   <li>Missing templates fail loudly with exceptions (not warnings)</li>
 *   <li>External CSS file linking works correctly</li>
 *   <li>Deployment structure includes CSS files</li>
 * </ul>
 */
class TemplateAndCssTest {

    @Test void templateLoadingFailsLoudly() throws Exception {
        // Use reflection to test private copyTemplate method
        ReportGenerator generator = new ReportGenerator();
        Method copyTemplate = ReportGenerator.class.getDeclaredMethod("copyTemplate", String.class, String.class);
        copyTemplate.setAccessible(true);

        // Test loading non-existent template throws IOException
        assertThrows(InvocationTargetException.class, () -> copyTemplate.invoke(generator, "non-existent-template.html", "."), "Loading non-existent template should throw exception");

        // Verify the underlying cause is IOException
        try {
            copyTemplate.invoke(generator, "non-existent-template.html", ".");
        } catch (InvocationTargetException e) {
            assertInstanceOf(IOException.class, e.getCause(), "Underlying exception should be IOException");
            assertTrue(e.getCause().getMessage().contains("non-existent-template"),
                    "Exception message should mention missing template");
        }
    }

    @Test void validTemplatesLoad(@TempDir Path tempDir) throws Exception {
        ReportGenerator generator = new ReportGenerator();
        String outputDir = tempDir.toString();

        // Test that template files can be copied
        assertDoesNotThrow(() -> {
            generator.copySupportFiles(outputDir);
        }, "Copying support files should not throw");

        // Verify that essential files were copied
        assertTrue(Files.exists(tempDir.resolve("report-styles.css")), "CSS file should be copied");
        assertTrue(Files.exists(tempDir.resolve("data-loader.js")), "JavaScript file should be copied");

        // Verify that HTML templates exist in resources
        assertNotNull(getClass().getResourceAsStream("/templates/index.html"), "Index template should exist");
        assertNotNull(getClass().getResourceAsStream("/templates/trends.html"), "Trends template should exist");
        assertNotNull(getClass().getResourceAsStream("/templates/detailed.html"), "Detailed template should exist");
    }

    @Test void cssFileIsIncludedInResources() {
        // Verify CSS file exists in resources
        try (InputStream cssStream = getClass().getClassLoader()
                .getResourceAsStream("templates/report-styles.css")) {
            assertNotNull(cssStream, "CSS file should exist in resources");

            // Read and verify CSS content
            String cssContent = new String(cssStream.readAllBytes());
            assertTrue(cssContent.contains("body"), "CSS should contain body styles");
            assertTrue(cssContent.contains(".nav-menu"), "CSS should contain nav-menu styles");
            assertTrue(cssContent.contains(".grade-"), "CSS should contain grade classes");
            assertTrue(cssContent.contains(".performance-badge"), "CSS should contain performance badge styles");
        } catch (IOException e) {
            fail("CSS file should be readable: " + e.getMessage());
        }
    }

    @Test void cssIsExternalInGeneratedHtml(@TempDir Path tempDir) throws Exception {
        // Use real test data
        Path sourceJson = Path.of("src/test/resources/library-benchmark-results/micro-benchmark-result.json");
        Path jsonFile = tempDir.resolve("micro-benchmark-result.json");
        Files.copy(sourceJson, jsonFile);

        ReportGenerator generator = new ReportGenerator();
        String outputDir = tempDir.toString();

        generator.generateIndexPage(jsonFile, BenchmarkType.MICRO, outputDir);
        generator.copySupportFiles(outputDir);

        Path indexFile = Path.of(outputDir, "index.html");
        String content = Files.readString(indexFile);

        // Verify CSS is linked externally
        assertTrue(content.contains("report-styles.css"), "Should link to external CSS file");

        // Verify CSS file exists
        Path cssFile = Path.of(outputDir, "report-styles.css");
        assertTrue(Files.exists(cssFile), "CSS file should exist");

        // Verify critical CSS classes are in the CSS file
        String cssContent = Files.readString(cssFile);
        assertTrue(cssContent.contains(".nav-menu"), "Should include nav-menu styles");
        assertTrue(cssContent.contains(".stats-grid"), "Should include stats-grid styles");
        assertTrue(cssContent.contains(".table"), "Should include table styles");
        assertTrue(cssContent.contains(".grade-a-plus"), "Should include grade-a-plus styles");
        assertTrue(cssContent.contains(".performance-badge"), "Should include performance-badge styles");
    }

    @Test void deploymentStructureIncludesCss(@TempDir Path tempDir) throws Exception {
        // Simulate a full report generation with all artifacts
        BenchmarkResultProcessor processor = new BenchmarkResultProcessor(BenchmarkType.MICRO);

        // Copy test JSON file for processing
        Path sourceJson = Path.of("src/test/resources/library-benchmark-results/micro-benchmark-result.json");
        Path targetJson = tempDir.resolve("micro-benchmark-result.json");
        Files.copy(sourceJson, targetJson);

        // BenchmarkResultProcessor expects empty collection but checks for JSON file
        String outputDir = tempDir.toString();

        // Process results to generate full deployment structure
        processor.processResults(List.of(), outputDir);

        // Verify HTML files exist
        assertTrue(Files.exists(Path.of(outputDir, "index.html")),
                "Index page should exist in deployment");
        assertTrue(Files.exists(Path.of(outputDir, "trends.html")),
                "Trends page should exist in deployment");

        // Verify HTML files reference external CSS
        String indexContent = Files.readString(Path.of(outputDir, "index.html"));
        assertTrue(indexContent.contains("report-styles.css"), "Index should reference CSS file");
        assertTrue(indexContent.contains("data-loader.js"), "Index should reference JS file");

        String trendsContent = Files.readString(Path.of(outputDir, "trends.html"));
        assertTrue(trendsContent.contains("report-styles.css"), "Trends should reference CSS file");
        assertTrue(trendsContent.contains("data-loader.js"), "Trends should reference JS file");
    }

    @Test void cssClassesForPerformanceGrades(@TempDir Path tempDir) throws Exception {
        // Use real test data
        Path sourceJson = Path.of("src/test/resources/library-benchmark-results/micro-benchmark-result.json");
        Path jsonFile = tempDir.resolve("micro-benchmark-result.json");
        Files.copy(sourceJson, jsonFile);

        ReportGenerator generator = new ReportGenerator();
        String outputDir = tempDir.toString();

        generator.generateIndexPage(jsonFile, BenchmarkType.MICRO, outputDir);
        generator.copySupportFiles(outputDir);

        Path cssFile = Path.of(outputDir, "report-styles.css");
        String content = Files.readString(cssFile);

        // Verify performance grade CSS classes are defined
        assertTrue(content.contains(".grade-a-plus"), "Should define A+ grade style");
        assertTrue(content.contains(".grade-a"), "Should define A grade style");
        assertTrue(content.contains(".grade-b"), "Should define B grade style");
        assertTrue(content.contains(".grade-c"), "Should define C grade style");
        assertTrue(content.contains(".grade-d"), "Should define D grade style");
        assertTrue(content.contains(".grade-f"), "Should define F grade style");

        // Verify color values for grades
        assertTrue(content.contains("#00c851"), "A+ should use green color");
        assertTrue(content.contains("#ffc107"), "B should use yellow color");
        assertTrue(content.contains("#dc3545"), "D should use red color");
    }

    @Test void responsiveCssStyles(@TempDir Path tempDir) throws Exception {
        // Use real test data
        Path sourceJson = Path.of("src/test/resources/library-benchmark-results/micro-benchmark-result.json");
        Path jsonFile = tempDir.resolve("micro-benchmark-result.json");
        Files.copy(sourceJson, jsonFile);

        ReportGenerator generator = new ReportGenerator();
        String outputDir = tempDir.toString();

        generator.generateIndexPage(jsonFile, BenchmarkType.MICRO, outputDir);
        generator.copySupportFiles(outputDir);

        Path cssFile = Path.of(outputDir, "report-styles.css");
        String cssContent = Files.readString(cssFile);

        Path indexFile = Path.of(outputDir, "index.html");
        String htmlContent = Files.readString(indexFile);

        // Verify responsive CSS is included
        assertTrue(cssContent.contains("@media"), "Should have media queries");
        assertTrue(cssContent.contains("max-width: 768px"), "Should have mobile breakpoint");
        assertTrue(cssContent.contains("grid-template-columns"), "Should have responsive grid");

        // Verify viewport meta tag
        assertTrue(htmlContent.contains("viewport"), "Should have viewport meta tag");
        assertTrue(htmlContent.contains("width=device-width"), "Should set device width");
    }

    @Test void missingCssFileHandling(@TempDir Path tempDir) throws Exception {
        // Test that CSS file exists and can be copied
        ReportGenerator generator = new ReportGenerator();
        String outputDir = tempDir.toString();

        // Test that we can copy the CSS file
        assertDoesNotThrow(() -> {
            generator.copySupportFiles(outputDir);
        }, "Copying CSS file should not throw");

        // Verify CSS file was copied and has content
        Path cssFile = tempDir.resolve("report-styles.css");
        assertTrue(Files.exists(cssFile), "CSS file should be copied");
        String cssContent = Files.readString(cssFile);
        assertNotNull(cssContent, "CSS should have content");
        assertTrue(cssContent.contains("body"), "CSS should contain body styles");
        assertTrue(cssContent.contains(".nav-menu"), "CSS should contain nav-menu styles");
    }
}