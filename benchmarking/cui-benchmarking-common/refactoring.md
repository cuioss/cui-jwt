# GitHubPagesGenerator Refactoring Plan - Eliminate Output Duplication

## ⚠️ CRITICAL: Task Execution Rules

**BEFORE STARTING ANY TASK**: Read and completely understand this entire document
- Understand the current architecture problems
- Understand the proposed solution
- Understand the impact on all three client modules
- Understand the migration strategy

**EXECUTION RULES**:
0. **Ask QUESTIONS** if anything is unclear before starting
1. **COMPLETE ONE TASK BEFORE STARTING THE NEXT** - No parallel work
2. **MARK TASK AS COMPLETED** immediately when done ✅
3. **NO TASK MAY BE STARTED** before the previous one is fully implemented
4. **ALL BUILD ERRORS/WARNINGS MUST BE FIXED** before moving to next task
5. **RUN PRE-COMMIT BUILD** after each phase to ensure no regressions
6. **TEST EACH MODULE** after changes to verify functionality

## Problem Statement

Currently, the report generation creates duplicate files:
- Files are first generated in `/target/benchmark-results/`
- Then COPIED to `/target/benchmark-results/gh-pages-ready/`
- This results in ~50% wasted disk space and unnecessary I/O operations

## Current Architecture Analysis

### Module Dependencies

```
┌─────────────────────────────────────────────────────────────────────┐
│                    CLIENT MODULES                                   │
├─────────────────────────────────────────────────────────────────────┤
│ 1. benchmark-integration-wrk    (WrkResultPostProcessor)            │
│ 2. benchmark-integration-quarkus (via AbstractBenchmarkRunner)      │
│ 3. benchmark-library            (via AbstractBenchmarkRunner)       │
└─────────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    cui-benchmarking-common                          │
├─────────────────────────────────────────────────────────────────────┤
│ • ReportGenerator         - Generates HTML files                    │
│ • ReportDataGenerator     - Generates data JSON files               │
│ • BadgeGenerator          - Generates badge JSON files              │
│ • GitHubPagesGenerator    - COPIES everything to gh-pages-ready/    │
│ • HistoricalDataManager   - Archives historical data                │
│ • PrometheusMetricsManager- Collects Prometheus metrics             │
└─────────────────────────────────────────────────────────────────────┘
```

### Current File Flow (DUPLICATED)

```
benchmark-results/
├── index.html                     ┐
├── trends.html                    │
├── detailed.html                  │
├── report-styles.css              │
├── data-loader.js                 ├─> ALL DUPLICATED IN gh-pages-ready/
├── badges/                        │
│   ├── *.json                     │
├── data/                          │
│   └── benchmark-data.json        ┘
├── prometheus/                    (kept separate, copied to gh-pages-ready/data/)
├── history/                        (kept separate, not copied)
└── gh-pages-ready/                ← DUPLICATION DESTINATION
    └── [Complete copy of above]
```

## Proposed Solution

### Target Architecture (NO DUPLICATION)

```
benchmark-results/
├── wrk/                           (WRK raw results - NOT for deployment)
├── prometheus/                    (Raw metrics - NOT for deployment)
├── history/                       (Historical archive - NOT for deployment)
├── quarkus-logs.txt              (Logs - NOT for deployment)
├── *.txt                         (Other raw outputs - NOT for deployment)
└── gh-pages-ready/               ← ALL DEPLOYABLE CONTENT WRITTEN DIRECTLY HERE
    ├── index.html
    ├── trends.html
    ├── detailed.html
    ├── 404.html
    ├── robots.txt
    ├── sitemap.xml
    ├── report-styles.css
    ├── data-loader.js
    ├── api/
    │   ├── benchmarks.json
    │   ├── latest.json
    │   └── status.json
    ├── badges/
    │   └── *.json
    └── data/
        ├── benchmark-data.json
        └── *-metrics.json (from prometheus/)
```

**Key Change**: All report generators write DIRECTLY to `gh-pages-ready/` subdirectory from the beginning. No intermediate files, no copying.

## Implementation Phases

## Phase 1: Create Output Directory Structure Management ✅

- [x] Create `OutputDirectoryStructure` class
  ```java
  package de.cuioss.benchmarking.common.output;

  public class OutputDirectoryStructure {
      private final Path benchmarkResultsDir;  // Root benchmark-results directory
      private final Path deploymentDir;        // gh-pages-ready directory
      private final Path htmlDir;              // gh-pages-ready (for HTML files)
      private final Path dataDir;              // gh-pages-ready/data
      private final Path badgesDir;            // gh-pages-ready/badges
      private final Path apiDir;               // gh-pages-ready/api

      // Non-deployed directories (in benchmark-results root)
      private final Path historyDir;           // benchmark-results/history
      private final Path prometheusRawDir;     // benchmark-results/prometheus
      private final Path wrkDir;               // benchmark-results/wrk (WRK module only)

      public OutputDirectoryStructure(Path benchmarkResultsDir) {
          this.benchmarkResultsDir = benchmarkResultsDir;
          this.deploymentDir = benchmarkResultsDir.resolve("gh-pages-ready");
          this.htmlDir = deploymentDir;
          this.dataDir = deploymentDir.resolve("data");
          this.badgesDir = deploymentDir.resolve("badges");
          this.apiDir = deploymentDir.resolve("api");

          // Non-deployed directories stay in root
          this.historyDir = benchmarkResultsDir.resolve("history");
          this.prometheusRawDir = benchmarkResultsDir.resolve("prometheus");
          this.wrkDir = benchmarkResultsDir.resolve("wrk");
      }

      public void ensureDirectories() { /* Create all directories */ }
      // Getters for each directory path
  }
  ```

- [x] Create comprehensive unit tests for OutputDirectoryStructure
  - Test directory creation
  - Test path resolution
  - Test with non-existent root directory
  - Test with existing directories

- [x] Run pre-commit build for cui-benchmarking-common
  ```bash
  ./mvnw -Ppre-commit clean verify -pl benchmarking/cui-benchmarking-common
  ```
  - Fix ALL checkstyle warnings
  - Fix ALL SpotBugs issues
  - Ensure 100% test pass rate

## Phase 2: Create Unified Report Generator

- [ ] Create `UnifiedReportGenerator` class that combines functionality
  ```java
  package de.cuioss.benchmarking.common.report;

  public class UnifiedReportGenerator {
      // Combines ReportGenerator + ReportDataGenerator + BadgeGenerator
      // Writes directly to gh-pages-ready/ using OutputDirectoryStructure

      public void generateAll(BenchmarkData data,
                              BenchmarkType type,
                              OutputDirectoryStructure structure) {
          // Generate HTML to gh-pages-ready/
          // Generate data JSON to gh-pages-ready/data/
          // Generate badges to gh-pages-ready/badges/
          // Generate API endpoints to gh-pages-ready/api/
          // Copy Prometheus metrics from prometheus/ to gh-pages-ready/data/
      }
  }
  ```

- [ ] Implement direct file writing to gh-pages-ready locations
  - HTML files → structure.getHtmlDir() (gh-pages-ready/)
  - Data files → structure.getDataDir() (gh-pages-ready/data/)
  - Badge files → structure.getBadgesDir() (gh-pages-ready/badges/)
  - API files → structure.getApiDir() (gh-pages-ready/api/)

- [ ] Add comprehensive logging
  - Log each file written
  - Log directory creation
  - Log any errors with context

- [ ] Create unit tests for UnifiedReportGenerator
  - Test all file generation
  - Test error handling
  - Test with missing data
  - Test with invalid paths

- [ ] Run pre-commit build and fix all issues

## Phase 3: Simplify GitHubPagesGenerator

- [ ] Since all files are now written directly to gh-pages-ready/, GitHubPagesGenerator becomes simpler
  - No more copying existing files
  - No more prepareDeploymentStructure with source/target
  - Just generate additional deployment files

- [ ] Update GitHubPagesGenerator to only generate deployment-specific files
  ```java
  public void generateDeploymentAssets(OutputDirectoryStructure structure) {
      // Generate 404.html to gh-pages-ready/
      // Generate robots.txt to gh-pages-ready/
      // Generate sitemap.xml to gh-pages-ready/
      // NO COPYING - everything else is already in gh-pages-ready/
  }
  ```

- [ ] Remove all copy methods
  - Remove copyHtmlFiles() - no longer needed
  - Remove copyBadgeFiles() - no longer needed
  - Remove copyDataFiles() - no longer needed
  - Remove copyPrometheusMetrics() - handled by UnifiedReportGenerator

- [ ] Update unit tests
  - Verify no duplication occurs
  - Verify only new files are created
  - Test backward compatibility wrapper

- [ ] Run pre-commit build and fix all issues

## Phase 4: Update benchmark-library Module (Simplest)

- [ ] Update BenchmarkResultProcessor to use new structure
  ```java
  // BEFORE:
  generateReports(benchmarkData, outputDir);
  generateGitHubPagesStructure(outputDir);

  // AFTER:
  OutputDirectoryStructure structure = new OutputDirectoryStructure(Path.of(outputDir));
  structure.ensureDirectories();
  unifiedReportGenerator.generateAll(benchmarkData, benchmarkType, structure);
  gitHubPagesGenerator.generateDeploymentAssets(structure);

  // Note: History stays in benchmark-results/history/
  // Everything deployable is in benchmark-results/gh-pages-ready/
  ```

- [ ] Test benchmark-library module
  ```bash
  ./mvnw clean verify -Pbenchmark -pl benchmarking/benchmark-library
  ```
  - Verify no duplicate files
  - Verify all files in correct locations
  - Verify benchmark results are correct

- [ ] Run full build with pre-commit profile
  ```bash
  ./mvnw -Ppre-commit clean verify -pl benchmarking/benchmark-library
  ```

## Phase 5: Update benchmark-integration-quarkus Module

- [ ] Update AbstractBenchmarkRunner.processResults()
  - Use OutputDirectoryStructure
  - Use UnifiedReportGenerator
  - Remove intermediate file generation

- [ ] Test with quick benchmark
  ```bash
  ./mvnw clean verify -Pbenchmark,quick -pl benchmarking/benchmark-integration-quarkus
  ```
  - Verify output structure
  - Check for any duplicates
  - Verify Prometheus metrics in data/

- [ ] Test with full benchmark
  ```bash
  ./mvnw clean verify -Pbenchmark -pl benchmarking/benchmark-integration-quarkus
  ```

- [ ] Run pre-commit build and fix all issues

## Phase 6: Update benchmark-integration-wrk Module

- [ ] Update WrkResultPostProcessor
  ```java
  // BEFORE:
  reportGenerator.generateIndexPage(data, type, outputDir);
  reportGenerator.generateTrendsPage(outputDir);
  reportGenerator.generateDetailedPage(outputDir);
  gitHubPagesGenerator.prepareDeploymentStructure(outputDir, outputDir + "/gh-pages-ready");

  // AFTER:
  OutputDirectoryStructure structure = new OutputDirectoryStructure(outputDir.toPath());
  structure.ensureDirectories();
  unifiedReportGenerator.generateAll(benchmarkData, BenchmarkType.INTEGRATION, structure);
  gitHubPagesGenerator.generateDeploymentAssets(structure);

  // Note: WRK raw results stay in benchmark-results/wrk/
  // Prometheus raw data stays in benchmark-results/prometheus/
  // Everything deployable is in benchmark-results/gh-pages-ready/
  ```

- [ ] Handle WRK-specific directories
  - Keep wrk/ subdirectory for raw results (already implemented)
  - Keep prometheus/ for raw metrics
  - Ensure these are NOT in gh-pages-ready/

- [ ] Test with quick benchmark
  ```bash
  ./mvnw clean verify -Pbenchmark,quick -pl benchmarking/benchmark-integration-wrk
  ```

- [ ] Verify directory structure
  ```bash
  find benchmarking/benchmark-integration-wrk/target/benchmark-results -type f | sort
  ```
  - gh-pages-ready/ directory SHOULD exist with all deployable content
  - Raw files (wrk/, prometheus/, *.txt) should be in benchmark-results/ root
  - No duplication between root and gh-pages-ready/

- [ ] Run pre-commit build and fix all issues

## Phase 7: Cleanup and Documentation

- [ ] Remove deprecated methods (if safe)
  - Check no external dependencies
  - Remove old ReportGenerator methods
  - Remove old GitHubPagesGenerator

- [ ] Update documentation
  - Update README files in each module
  - Document new output structure
  - Add migration guide for external users

- [ ] Create integration test
  - Test all three modules together
  - Verify consistent output structure
  - Verify no regressions

- [ ] Final verification - run all benchmarks
  ```bash
  # Library benchmarks
  ./mvnw clean verify -Pbenchmark -pl benchmarking/benchmark-library

  # Integration benchmarks (quick mode)
  ./mvnw clean verify -Pbenchmark,quick -pl benchmarking/benchmark-integration-quarkus
  ./mvnw clean verify -Pbenchmark,quick -pl benchmarking/benchmark-integration-wrk
  ```

- [ ] Verify GitHub Pages deployment readiness
  - Check that output can be directly deployed
  - No nested gh-pages-ready directory
  - All links work correctly

## Success Criteria

1. **No file duplication** - Each file exists only once
2. **Clean separation** - Deployable content ONLY in gh-pages-ready/, raw data outside
3. **Direct deployment** - gh-pages-ready/ is directly deployable to GitHub Pages
4. **Backward compatibility** - Old code still works during migration
5. **All tests pass** - 100% test success rate
6. **No build warnings** - Clean pre-commit builds
7. **Performance improvement** - Reduced I/O operations by ~50% (no copying)
8. **Clear structure** - Easy to identify what gets deployed vs what stays local

## Rollback Plan

If issues occur during migration:
1. Keep old classes with @Deprecated annotation
2. Add feature flag to switch between old/new behavior
3. Document known issues and workarounds
4. Plan incremental migration if needed

## Notes

- Each phase should be completed and tested before moving to the next
- Create a git commit after each successful phase
- If errors occur, fix them completely before proceeding
- Run benchmarks after each module update to verify functionality