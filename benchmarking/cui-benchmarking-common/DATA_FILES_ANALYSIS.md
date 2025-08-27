# Data Files Analysis - Benchmark Report System

## Overview
This document analyzes all JSON data files created in the benchmark report system and identifies which ones are actually used.

## Data Files Created

### Micro Benchmarks Directory (`/data/`)
1. **benchmark-data.json** ✅ USED
   - Primary data file for templates
   - Contains all processed report data
   - Used by: data-loader.js via `fetch('data/benchmark-data.json')`

2. **benchmark-result.json** ✅ USED
   - Original JMH benchmark results (copy of source)
   - Used by: JMH Visualizer iframe integration
   - Referenced in: detailed.html for download

3. **metrics.json** ✅ USED
   - Performance metrics and statistics
   - Referenced in: detailed.html, navigation-menu.html as links

4. **benchmark-summary.json** ❌ NOT USED
   - Created by SummaryGenerator
   - Not referenced in any templates

5. **summary.json** ❌ NOT USED
   - Created but not referenced in templates

6. **micro-benchmark-result.json** ❌ NOT USED
   - Copy of original input file
   - Not referenced in templates

7. **Individual metric files** ❌ NOT USED
   - SimpleCoreValidation_measureAverageTime-metrics.json
   - SimpleCoreValidation_measureConcurrentValidation-metrics.json
   - SimpleCoreValidation_measureThroughput-metrics.json
   - SimpleErrorLoad_validateMixedTokens0-metrics.json
   - SimpleErrorLoad_validateMixedTokens50-metrics.json
   - Created by MetricsGenerator but not referenced

### Integration Benchmarks Directory (`/data/`)
Same pattern as micro benchmarks:
- **benchmark-data.json** ✅ USED
- **benchmark-result.json** ✅ USED
- **metrics.json** ✅ USED
- **benchmark-summary.json** ❌ NOT USED
- **summary.json** ❌ NOT USED
- **integration-benchmark-result.json** ❌ NOT USED
- Individual metric files (JwtHealth_*, JwtValidation_*) ❌ NOT USED

### Badge Files (`/badges/`)
- **performance-badge.json** ✅ USED (by GitHub Pages API)
- **trend-badge.json** ❌ NOT USED (created but not integrated)
- **last-run-badge.json** ❌ NOT USED (created but not integrated)
- **integration-performance-badge.json** ✅ USED (by GitHub Pages API)

## Files Actually Used by Templates

### Core Files (Essential)
1. **benchmark-data.json** - Main data source for all dynamic content
2. **benchmark-result.json** - Required for JMH Visualizer integration
3. **metrics.json** - Referenced as downloadable raw data

### Files NOT Used (Can be Removed)
1. **benchmark-summary.json** - Redundant with benchmark-data.json
2. **summary.json** - Redundant with benchmark-data.json
3. **micro-benchmark-result.json** / **integration-benchmark-result.json** - Duplicates
4. Individual metric files (*-metrics.json) - Not referenced anywhere
5. **trend-badge.json** - Created but not integrated
6. **last-run-badge.json** - Created but not integrated

## Recommendations

### Keep Creating
- benchmark-data.json (primary data source)
- benchmark-result.json (for JMH Visualizer)
- metrics.json (for raw data access)
- performance-badge.json files (for GitHub Pages)

### Consider Removing
- All individual metric files (*-metrics.json)
- summary.json and benchmark-summary.json
- Duplicate result files (micro-benchmark-result.json, integration-benchmark-result.json)
- Unused badge files (trend-badge.json, last-run-badge.json)

### Potential Optimizations
1. Merge metrics.json content into benchmark-data.json
2. Remove generation of unused individual metric files
3. Simplify badge generation to only create performance badges

## File Generation Sources

### ReportDataGenerator.java
- Creates: benchmark-data.json (primary)
- Copies: benchmark-result.json (for visualizer)

### MetricsGenerator.java
- Creates: metrics.json
- Creates: Individual *-metrics.json files (not used)

### SummaryGenerator.java
- Creates: summary.json, benchmark-summary.json (not used)

### BadgeGenerator.java
- Creates: performance-badge.json (used)
- Creates: trend-badge.json, last-run-badge.json (not used)

### BenchmarkResultProcessor.java
- Copies: micro-benchmark-result.json / integration-benchmark-result.json (not used)