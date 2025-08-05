# Benchmark Processing Scripts

This directory contains scripts for processing and visualizing benchmark results in the CI/CD pipeline.

## Overview

The benchmark processing workflow has been modularized into focused scripts that handle different aspects of the benchmark pipeline:

### Core Scripts

1. **process-all-benchmarks.sh** - Master orchestration script
   - Coordinates all benchmark processing
   - Handles both micro and integration benchmarks
   - Creates processing status reports
   - Entry point from GitHub Actions

2. **process-micro-benchmarks.sh** - JMH micro benchmark processing
   - Processes JMH benchmark results
   - Creates performance badges
   - Updates performance tracking
   - Generates trend badges

3. **process-integration-benchmarks.sh** - Integration benchmark processing
   - Processes health check and JWT validation results
   - Creates integration-specific badges
   - Calculates JWT overhead metrics
   - Prepares data for visualization

4. **prepare-github-pages.sh** - GitHub Pages structure preparation
   - Sets up unified directory structure
   - Copies templates and resources
   - Prepares data directory
   - Ensures all visualization assets are in place

### Supporting Scripts

- **create-unified-performance-badge.sh** - Creates all performance and informational badges (handles both micro and integration)
- **create-performance-tracking.sh** - Manages historical performance data
- **update-performance-trends.sh** - Updates trend analysis
- **create-unified-trend-badge.sh** - Creates consolidated trend badges

### Utility Scripts

- **serve-local.sh** - Local HTTP server for testing (Python-based)
- **serve-local.js** - Local HTTP server for testing (Node.js-based)
- **prepare-step-metrics.sh** - Prepares step-by-step metrics data

### Utility Libraries

- **lib/badge-utils.sh** - Common badge creation functions and color calculations
- **lib/metrics-utils.sh** - Common metric calculation and conversion utilities

## Usage

### From GitHub Actions

The main entry point is called from `.github/workflows/benchmark.yml`:

```bash
bash benchmarking/benchmark-library/scripts/process-all-benchmarks.sh \
  benchmark-results \
  benchmarking/benchmark-library/doc/templates \
  gh-pages \
  "${{ github.sha }}" \
  "$TIMESTAMP" \
  "$TIMESTAMP_WITH_TIME"
```

### Local Testing

To test the visualization locally:

```bash
cd benchmarking/benchmark-library/scripts
./serve-local.sh

# Then open http://localhost:8080 in your browser
```

## Output Structure

The scripts create a unified GitHub Pages structure:

```
gh-pages/
├── index-visualizer.html         # Micro benchmarks visualization
├── integration-index.html        # Integration benchmarks visualization
├── step-metrics-visualizer.html  # Step-by-step metrics
├── performance-trends.html       # Historical trends
├── data/                         # JSON data files
│   ├── jmh-result.json
│   ├── integration-result.json
│   ├── jwt-validation-metrics.json
│   └── performance-tracking.json
├── resources/                    # Shared CSS/JS resources
│   ├── common-styles.css
│   ├── navigation.js
│   └── ...
├── badges/                       # Generated badge JSON files
│   ├── performance-badge.json
│   ├── trend-badge.json
│   └── ...
└── processing-results.json       # Processing status report
```

## Key Features

1. **Modular Design** - Each script has a focused responsibility
2. **Error Handling** - Scripts track and report processing status
3. **Badge Generation** - Automatic creation of shields.io compatible badges
4. **Trend Analysis** - Historical performance tracking and trend calculation
5. **Unified Navigation** - All pages share common navigation and resources

## Migration Notes

This script structure was migrated from inline GitHub Actions workflow logic to improve:
- Maintainability
- Testability  
- Reusability
- Local development experience

The migration preserved all functionality while making the process more transparent and easier to debug.

## Architecture Improvements

The scripts have been consolidated and reorganized for better maintainability:

- **Reduced from 16 to 11 scripts** by removing redundant functionality
- **Eliminated circular dependencies** by consolidating badge creation
- **Extracted common utilities** to `lib/` directory for better code reuse
- **Improved separation of concerns** with focused, single-purpose scripts

### Script Reduction Summary

**Removed Scripts:**
- `process-integration-results.sh` - Legacy script with no references
- `calculate-trend-badge.sh` - Functionality absorbed by unified trend script
- `create-performance-badge.sh` - Merged into unified performance badge script
- `create-simple-badge.sh` - Integrated into unified performance badge script

**Added Libraries:**
- `lib/badge-utils.sh` - Common badge functions
- `lib/metrics-utils.sh` - Common metric calculations