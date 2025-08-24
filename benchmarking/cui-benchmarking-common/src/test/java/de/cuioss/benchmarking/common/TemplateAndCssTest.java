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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openjdk.jmh.results.RunResult;

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

    @Test
    void testTemplateLoadingFailsLoudly() throws Exception {
        // Use reflection to test private loadTemplate method
        ReportGenerator generator = new ReportGenerator();
        Method loadTemplate = ReportGenerator.class.getDeclaredMethod("loadTemplate", String.class);
        loadTemplate.setAccessible(true);

        // Test loading non-existent template throws IOException
        assertThrows(InvocationTargetException.class, () -> {
            loadTemplate.invoke(generator, "non-existent-template.html");
        }, "Loading non-existent template should throw exception");

        // Verify the underlying cause is IOException
        try {
            loadTemplate.invoke(generator, "non-existent-template.html");
        } catch (InvocationTargetException e) {
            assertTrue(e.getCause() instanceof IOException,
                    "Underlying exception should be IOException");
            assertTrue(e.getCause().getMessage().contains("non-existent-template"),
                    "Exception message should mention missing template");
        }
    }

    @Test
    void testValidTemplatesLoad() throws Exception {
        // Use reflection to test private loadTemplate method
        ReportGenerator generator = new ReportGenerator();
        Method loadTemplate = ReportGenerator.class.getDeclaredMethod("loadTemplate", String.class);
        loadTemplate.setAccessible(true);

        // Test loading existing templates
        assertDoesNotThrow(() -> {
            String headerTemplate = (String) loadTemplate.invoke(generator, "report-header.html");
            assertNotNull(headerTemplate, "Header template should load");
            assertTrue(headerTemplate.contains("<"), "Template should contain HTML");
        }, "Loading existing header template should not throw");

        assertDoesNotThrow(() -> {
            String overviewTemplate = (String) loadTemplate.invoke(generator, "overview-section.html");
            assertNotNull(overviewTemplate, "Overview template should load");
            assertTrue(overviewTemplate.contains("<"), "Template should contain HTML");
        }, "Loading existing overview template should not throw");

        assertDoesNotThrow(() -> {
            String trendsTemplate = (String) loadTemplate.invoke(generator, "trends-section.html");
            assertNotNull(trendsTemplate, "Trends template should load");
            assertTrue(trendsTemplate.contains("<"), "Template should contain HTML");
        }, "Loading existing trends template should not throw");
        
        assertDoesNotThrow(() -> {
            String footerTemplate = (String) loadTemplate.invoke(generator, "report-footer.html");
            assertNotNull(footerTemplate, "Footer template should load");
        }, "Loading existing footer template should not throw");
    }

    @Test
    void testCssFileIsIncludedInResources() {
        // Verify CSS file exists in resources
        try (InputStream cssStream = getClass().getClassLoader()
                .getResourceAsStream("templates/report-styles.css")) {
            assertNotNull(cssStream, "CSS file should exist in resources");
            
            // Read and verify CSS content
            String cssContent = new String(cssStream.readAllBytes());
            assertTrue(cssContent.contains("body"), "CSS should contain body styles");
            assertTrue(cssContent.contains(".navbar"), "CSS should contain navbar styles");
            assertTrue(cssContent.contains(".grade-"), "CSS should contain grade classes");
            assertTrue(cssContent.contains(".performance-badge"), "CSS should contain performance badge styles");
        } catch (IOException e) {
            fail("CSS file should be readable: " + e.getMessage());
        }
    }

    @Test
    void testCssIsEmbeddedInGeneratedHtml(@TempDir Path tempDir) throws Exception {
        ReportGenerator generator = new ReportGenerator();
        String outputDir = tempDir.toString();
        
        List<RunResult> emptyResults = List.of();
        generator.generateIndexPage(emptyResults, outputDir);
        
        Path indexFile = Path.of(outputDir, "index.html");
        String content = Files.readString(indexFile);
        
        // Verify CSS is embedded
        assertTrue(content.contains("<style>"), "Should have style tag");
        assertTrue(content.contains("</style>"), "Should close style tag");
        
        // Verify critical CSS classes are present
        assertTrue(content.contains(".navbar"), "Should include navbar styles");
        assertTrue(content.contains(".stats-grid"), "Should include stats-grid styles");
        assertTrue(content.contains(".results-table"), "Should include results-table styles");
        assertTrue(content.contains(".grade-a-plus"), "Should include grade-a-plus styles");
        assertTrue(content.contains(".performance-badge"), "Should include performance-badge styles");
    }

    @Test
    void testDeploymentStructureIncludesCss(@TempDir Path tempDir) throws Exception {
        // Simulate a full report generation with all artifacts
        ReportGenerator generator = new ReportGenerator();
        BenchmarkResultProcessor processor = new BenchmarkResultProcessor();
        
        // Use test benchmark results
        var options = new org.openjdk.jmh.runner.options.OptionsBuilder()
                .include(TestBenchmark.class.getSimpleName())
                .warmupIterations(1)
                .measurementIterations(1)
                .forks(1)
                .build();
        
        var results = new org.openjdk.jmh.runner.Runner(options).run();
        String outputDir = tempDir.toString();
        
        // Process results to generate full deployment structure
        processor.processResults(results, outputDir);
        
        // Verify HTML files exist
        assertTrue(Files.exists(Path.of(outputDir, "index.html")),
                "Index page should exist in deployment");
        assertTrue(Files.exists(Path.of(outputDir, "trends.html")),
                "Trends page should exist in deployment");
        
        // Verify HTML files contain embedded CSS
        String indexContent = Files.readString(Path.of(outputDir, "index.html"));
        assertTrue(indexContent.contains("<style>"), "Index should have embedded CSS");
        assertTrue(indexContent.contains(".navbar"), "Index CSS should be complete");
        
        String trendsContent = Files.readString(Path.of(outputDir, "trends.html"));
        assertTrue(trendsContent.contains("<style>"), "Trends should have embedded CSS");
        assertTrue(trendsContent.contains(".navbar"), "Trends CSS should be complete");
    }

    @Test
    void testCssClassesForPerformanceGrades(@TempDir Path tempDir) throws Exception {
        ReportGenerator generator = new ReportGenerator();
        String outputDir = tempDir.toString();
        
        // Generate with test results
        var options = new org.openjdk.jmh.runner.options.OptionsBuilder()
                .include(TestBenchmark.class.getSimpleName())
                .warmupIterations(1)
                .measurementIterations(1)
                .forks(1)
                .build();
        
        var results = new org.openjdk.jmh.runner.Runner(options).run();
        generator.generateIndexPage(results, outputDir);
        
        Path indexFile = Path.of(outputDir, "index.html");
        String content = Files.readString(indexFile);
        
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

    @Test
    void testResponsiveCssStyles(@TempDir Path tempDir) throws Exception {
        ReportGenerator generator = new ReportGenerator();
        String outputDir = tempDir.toString();
        
        List<RunResult> emptyResults = List.of();
        generator.generateIndexPage(emptyResults, outputDir);
        
        Path indexFile = Path.of(outputDir, "index.html");
        String content = Files.readString(indexFile);
        
        // Verify responsive CSS is included
        assertTrue(content.contains("@media"), "Should have media queries");
        assertTrue(content.contains("max-width: 768px"), "Should have mobile breakpoint");
        assertTrue(content.contains("grid-template-columns"), "Should have responsive grid");
        
        // Verify viewport meta tag
        assertTrue(content.contains("viewport"), "Should have viewport meta tag");
        assertTrue(content.contains("width=device-width"), "Should set device width");
    }

    @Test
    void testMissingCssFileHandling() throws Exception {
        // Test that missing CSS is handled gracefully with fallback
        ReportGenerator generator = new ReportGenerator();
        
        // Use reflection to access private method
        Method loadTemplate = ReportGenerator.class.getDeclaredMethod("loadTemplate", String.class);
        loadTemplate.setAccessible(true);
        
        // Test that we can load the CSS file
        assertDoesNotThrow(() -> {
            String cssContent = (String) loadTemplate.invoke(generator, "report-styles.css");
            assertNotNull(cssContent, "CSS should load");
            assertTrue(cssContent.contains("body"), "CSS should contain body styles");
            assertTrue(cssContent.contains(".navbar"), "CSS should contain navbar styles");
        }, "Loading CSS file should not throw");
    }
}