# Benchmarking Refactoring Tasks

## Purpose

This document describes the refactoring tasks for the benchmarking aggregator and submodules (cui-benchmarking-common, benchmark-library, benchmark-integration-quarkus) to reduce duplication, unify configuration, improve portability/UX, and strengthen reporting/CI with minimal disruption.

## Pre-1.0 Development Rules

**IMPORTANT:** This project is in pre-1.0 phase. The following rules apply:

* **No deprecations:** Remove or replace code directly without @Deprecated annotations
* **No transitional comments:** Do not add comments like "// TODO: Remove in version X" or "// Legacy code"
* **Direct changes:** Make breaking changes immediately without migration paths
* **Clean codebase:** Remove unused code rather than marking it deprecated

## Guiding Principles

* Prefer configuration over code duplication
* Keep runners simple; push logic into shared infrastructure
* Minimize shell script complexity (under 50 lines)
* No Hamcrest dependencies
* Fail early and loud - no silent fallbacks

## Task Completion Process

After implementing any refactoring task:

1. **Quality Verification:** `./mvnw -Ppre-commit clean verify -DskipTests -pl benchmarking/[module-name]`
    - Fix all errors and warnings (mandatory)

2. **Run Benchmarks:** Verify benchmark execution:
    - Library: `./mvnw --no-transfer-progress clean verify -pl benchmarking/benchmark-library -Pbenchmark`
    - Quarkus: `./mvnw --no-transfer-progress clean verify -pl benchmarking/benchmark-integration-quarkus -Pbenchmark-testing`
    - Do not cancel; verify all outputs are correct

3. **Final Verification:** `./mvnw clean install -pl benchmarking/[module-name]`
    - Must complete without errors or warnings

4. **Update Documentation:** Mark task as complete with [x] checkbox

5. **Commit:** Use task identifier in commit message (e.g., "refactor: C2. Property and Path Unification")

## Rollback Strategy

Each task is self-contained; if issues arise, revert module-level changes while keeping the parent stable.

## Tasks

### Code Structure and Design

#### C2. Property and Path Unification
[x] **Priority:** High

**Description:** Standardize on benchmark.results.dir across code and POMs; remove legacy aliases. Update de.cuioss.benchmarking.common.BenchmarkRunner#getOutputDirectory() to read benchmark.results.dir first, then fall back to existing defaults. Ensure the JFR runner respects the same property and uses ${jmh.result.filePrefix} consistently.

**Rationale:** Consistent property naming reduces confusion and simplifies configuration management across modules.

#### C3. Runner Consolidation and Config Builder Adoption
[x] **Priority:** Medium

**Description:** Prefer BenchmarkConfiguration.fromSystemProperties() in all runners over direct BenchmarkOptionsHelper calls. Make the common BenchmarkRunner the default entrypoint for both modules by controlling include and jmh.* properties via POM/system properties. Keep a dedicated JFR runner but extract shared logic into common code; optionally support -Djfr.enabled=true in the common runner. Remove redundant module-specific option parsing.

**Rationale:** Reduces code duplication and provides a unified configuration approach across all benchmark modules.

#### C4. API Cleanup
[x] **Priority:** Low

**Description:** Remove BenchmarkOptionsHelper methods where superseded by BenchmarkConfiguration (no deprecation in pre-1.0). Clarify BenchmarkResultProcessor type detection and allow explicit override via property benchmark.type=micro|integration.

**Rationale:** Clean APIs without deprecated code reduce confusion and technical debt.

#### C5. Explicit Benchmark Type Parameter
[x] **Priority:** Medium

**Description:** Modify benchmark runners to explicitly pass benchmark type as a parameter instead of relying on auto-detection. The calling benchmark knows whether it's micro or integration. Add benchmark.type parameter to BenchmarkResultProcessor and require it to be set explicitly by the runners.

**Rationale:** The benchmark runner knows its type definitively. Explicit parameters are clearer than heuristic-based detection.

### Performance Improvements

#### P1. Metrics and Badges Calibration
[x] **Priority:** Medium

**Description:** Externalize performance grade thresholds via system property (e.g., benchmark.grade.thresholds=A+:1000000,A:100000,B:10000,...). Emit optional latency percentiles (p50/p90/p99) when available into data/metrics.json.

**Rationale:** Configurable thresholds allow project-specific performance requirements without code changes.

#### P2. Adjust Integration Benchmark Quality Gates
[x] **Priority:** High

**Description:** Adjust quality gate thresholds for integration benchmarks to realistic HTTP operation expectations. Current threshold of 1000 ops/s fails with actual 9.17 ops/ms. Set appropriate thresholds: 5 ops/ms for HTTP operations.

**Rationale:** Integration benchmarks include network/TLS overhead and should have different performance expectations than micro benchmarks.

#### P3. Performance Score CSS Classes
[x] **Priority:** Medium

**Description:** Enhance performance score calculation to use fixed CSS classes from report-styles.css. Add grade-specific CSS classes (e.g., .grade-a-plus, .grade-a, .grade-b) in the stylesheet. Update BadgeGenerator and ReportGenerator to apply these classes based on performance scores instead of inline styles or hardcoded colors.

**Rationale:** Consistent visual representation of performance grades through CSS enables theme customization without code changes.

#### P4. Trend Analysis System
[ ] **Priority:** Low

**Description:** Develop a comprehensive trend analysis system for performance badges with historical data comparison. Support performance history tracking across CI runs.

**Rationale:** Historical trends enable detection of performance regressions and improvements over time.

### Testing Improvements

#### T1. Baseline and Safety Verification
[ ] **Priority:** High

**Description:** Capture current behavior and artifacts as a baseline for comparison. Ensure ANALYSIS.md reflects the actual state; keep for traceability. Verify that fresh runs produce target/benchmark-results with badges, reports, data, gh-pages-ready, summary.

**Rationale:** Establishes a reference point for validating that refactoring doesn't introduce regressions.

#### T2. Template and CSS Linking Tests
[ ] **Priority:** Medium

**Description:** Add specific tests for template loading failures and CSS linking behavior. Test that missing templates fail loudly with exceptions (not warnings). Verify external CSS file linking works correctly. Test that deployment structure includes CSS files.

**Rationale:** Template and CSS handling are critical for report generation and must fail explicitly when resources are missing.

#### T3. Review Java and HTML Code Quality
[ ] **Priority:** Medium

**Description:** Use IDE diagnostics to find potential errors and warnings in all Java and HTML code across all benchmark modules. Fix all identified issues. This includes:
- Fix all SonarQube warnings shown in IDE diagnostics (code smells, vulnerabilities, bugs)
- Remove obvious/nonsense comments (e.g., "// Constructor", "// Getter methods", "// This method does X")
- Fix IDE warnings and code inspection issues
- Ensure compliance with CUI coding guidelines
- Remove any remaining TODO/FIXME/HACK comments
- Verify proper JavaDoc without stating the obvious
- Address SonarQube issues like: cognitive complexity, duplicated code blocks, unused imports, magic numbers

**Rationale:** Proactive quality checks prevent runtime issues and improve code maintainability. Clean code without redundant comments improves readability. SonarQube compliance ensures consistent code quality standards.

#### T4. Error Handling Strictness
[ ] **Priority:** Medium

**Description:** Replace all warning logs with exceptions for missing resources. Remove all fallback mechanisms in template loading - fail immediately if templates are missing. Ensure all file operations throw exceptions on failure rather than logging warnings. No silent failures allowed.

**Rationale:** Fail-fast principle ensures problems are detected immediately in development/CI rather than producing incorrect output in production.

### Dependency Management

#### D1. Build Portability and Duplication Reduction
[ ] **Priority:** High

**Description:** Remove exec-maven-plugin "mkdir" executions; let code create directories or use build-helper-maven-plugin/maven-antrun-plugin as needed. Move common exec-maven-plugin and maven-dependency-plugin config into benchmarking/pom.xml under <pluginManagement>; reference from modules with minimal overrides.

**Rationale:** Platform-agnostic builds ensure consistent behavior across Windows/Linux/macOS environments.

### Documentation Improvements

#### DOC2. CI and GitHub Pages Publishing
[ ] **Priority:** Low

**Description:** Document a lightweight GH Actions job that runs selected benchmarks on demand and publishes gh-pages-ready/ to the gh-pages branch. Optionally persist historical data/ to maintain trends across runs. Include example workflow in doc/ with README pointers and local dry-run instructions.

**Rationale:** Automated publishing reduces manual effort and ensures consistent benchmark reporting.

#### DOC3. Template Variable Documentation
[ ] **Priority:** Low

**Description:** Document all template variable substitution patterns used in HTML templates. Create a reference guide listing all available variables (${variableName}) and their expected values for each template file.

**Rationale:** Clear documentation enables customization and troubleshooting of report templates.

