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
package de.cuioss.jwt.benchmarking.common.github;

import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Generator for GitHub Pages deployment structure.
 * Prepares all benchmark artifacts in a deployment-ready directory structure
 * that can be directly copied to GitHub Pages without additional processing.
 * 
 * @author CUI Benchmarking Infrastructure
 */
public class GitHubPagesGenerator {
    
    /**
     * Prepare complete GitHub Pages deployment structure.
     * 
     * @param sourceDir source directory containing all generated artifacts
     * @param targetDir target directory for GitHub Pages ready structure
     */
    public void prepareDeploymentStructure(String sourceDir, String targetDir) throws IOException {
        Path source = Paths.get(sourceDir);
        Path target = Paths.get(targetDir);
        
        // Ensure target directory exists and is clean
        if (Files.exists(target)) {
            FileUtils.deleteDirectory(target.toFile());
        }
        Files.createDirectories(target);
        
        System.out.println("📁 Preparing GitHub Pages structure...");
        
        // Copy HTML reports as main pages
        copyHtmlReports(source, target);
        
        // Copy badges for shields.io consumption
        copyBadges(source, target);
        
        // Copy metrics data for API consumption
        copyMetricsData(source, target);
        
        // Create API endpoints structure
        createApiStructure(source, target);
        
        // Create GitHub Pages configuration
        createGitHubPagesConfig(target);
        
        System.out.println("✅ GitHub Pages structure ready at: " + targetDir);
    }
    
    /**
     * Copy HTML reports to root and appropriate subdirectories.
     */
    private void copyHtmlReports(Path source, Path target) throws IOException {
        Path indexFile = source.resolve("index.html");
        if (Files.exists(indexFile)) {
            Files.copy(indexFile, target.resolve("index.html"), StandardCopyOption.REPLACE_EXISTING);
        }
        
        // Copy any detailed reports
        Files.walk(source)
             .filter(Files::isRegularFile)
             .filter(p -> p.toString().endsWith(".html"))
             .forEach(htmlFile -> {
                 try {
                     String filename = htmlFile.getFileName().toString();
                     Files.copy(htmlFile, target.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
                 } catch (IOException e) {
                     System.err.println("Warning: Could not copy " + htmlFile + ": " + e.getMessage());
                 }
             });
    }
    
    /**
     * Copy badges to api/badges/ directory for shields.io consumption.
     */
    private void copyBadges(Path source, Path target) throws IOException {
        Path badgesSource = source.resolve("badges");
        if (!Files.exists(badgesSource)) {
            return;
        }
        
        Path badgesTarget = target.resolve("api/badges");
        Files.createDirectories(badgesTarget);
        
        Files.walk(badgesSource)
             .filter(Files::isRegularFile)
             .filter(p -> p.toString().endsWith(".json"))
             .forEach(badgeFile -> {
                 try {
                     String filename = badgeFile.getFileName().toString();
                     Files.copy(badgeFile, badgesTarget.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
                 } catch (IOException e) {
                     System.err.println("Warning: Could not copy badge " + badgeFile + ": " + e.getMessage());
                 }
             });
    }
    
    /**
     * Copy metrics data to api/data/ directory.
     */
    private void copyMetricsData(Path source, Path target) throws IOException {
        Path dataSource = source.resolve("data");
        if (!Files.exists(dataSource)) {
            return;
        }
        
        Path dataTarget = target.resolve("api/data");
        Files.createDirectories(dataTarget);
        
        Files.walk(dataSource)
             .filter(Files::isRegularFile)
             .filter(p -> p.toString().endsWith(".json"))
             .forEach(dataFile -> {
                 try {
                     String filename = dataFile.getFileName().toString();
                     Files.copy(dataFile, dataTarget.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
                 } catch (IOException e) {
                     System.err.println("Warning: Could not copy data " + dataFile + ": " + e.getMessage());
                 }
             });
    }
    
    /**
     * Create API structure with index files for easy discovery.
     */
    private void createApiStructure(Path source, Path target) throws IOException {
        Path apiDir = target.resolve("api");
        Files.createDirectories(apiDir);
        
        // Create API index
        String apiIndex = "{\n" +
                         "  \"endpoints\": {\n" +
                         "    \"badges\": \"/api/badges/\",\n" +
                         "    \"data\": \"/api/data/\",\n" +
                         "    \"summary\": \"/api/benchmark-summary.json\"\n" +
                         "  },\n" +
                         "  \"description\": \"CUI JWT Benchmark API\",\n" +
                         "  \"version\": \"1.0\"\n" +
                         "}";
        
        Files.write(apiDir.resolve("index.json"), apiIndex.getBytes());
        
        // Copy benchmark summary if available
        Path summarySource = source.resolve("benchmark-summary.json");
        if (Files.exists(summarySource)) {
            Files.copy(summarySource, apiDir.resolve("benchmark-summary.json"), StandardCopyOption.REPLACE_EXISTING);
        }
    }
    
    /**
     * Create GitHub Pages configuration files.
     */
    private void createGitHubPagesConfig(Path target) throws IOException {
        // Create _config.yml for Jekyll (if needed)
        String configYml = "# GitHub Pages configuration for CUI JWT Benchmarks\n" +
                          "title: CUI JWT Benchmark Results\n" +
                          "description: Performance benchmarks for CUI JWT validation library\n" +
                          "theme: minima\n" +
                          "\n" +
                          "plugins:\n" +
                          "  - jekyll-feed\n" +
                          "\n" +
                          "# Serve JSON files with correct content-type\n" +
                          "plugins:\n" +
                          "  - jekyll-optional-front-matter\n" +
                          "\n" +
                          "include:\n" +
                          "  - api\n";
        
        Files.write(target.resolve("_config.yml"), configYml.getBytes());
        
        // Create README.md for the pages site
        String readme = "# CUI JWT Benchmark Results\n\n" +
                       "This site contains performance benchmark results for the CUI JWT validation library.\n\n" +
                       "## API Endpoints\n\n" +
                       "- **Badges**: `/api/badges/` - Shields.io compatible badge data\n" +
                       "- **Metrics**: `/api/data/` - Detailed performance metrics\n" +
                       "- **Summary**: `/api/benchmark-summary.json` - High-level benchmark summary\n\n" +
                       "## Reports\n\n" +
                       "- [Benchmark Results](./index.html) - Interactive performance report\n\n" +
                       "Generated automatically by the CUI Benchmarking Infrastructure.\n";
        
        Files.write(target.resolve("README.md"), readme.getBytes());
    }
}