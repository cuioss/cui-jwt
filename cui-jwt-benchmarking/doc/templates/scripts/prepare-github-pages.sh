#!/bin/bash
# Prepare GitHub Pages structure and copy benchmark results
# Usage: prepare-github-pages.sh <benchmark-results-dir> <templates-dir> <output-dir>

set -e

BENCHMARK_RESULTS_DIR="$1"
TEMPLATES_DIR="$2"
OUTPUT_DIR="$3"

echo "Preparing GitHub Pages structure..."

# Create directory for GitHub Pages
mkdir -p "$OUTPUT_DIR"

# Copy benchmark results to gh-pages directory
if [ -d "$BENCHMARK_RESULTS_DIR" ]; then
  cp -r "$BENCHMARK_RESULTS_DIR"/* "$OUTPUT_DIR"/
  echo "Copied benchmark results from $BENCHMARK_RESULTS_DIR"
else
  echo "Warning: Benchmark results directory not found: $BENCHMARK_RESULTS_DIR"
fi

# Find and rename the JMH result file for visualization
if [ -f "jmh-result.json" ]; then
  echo "Using jmh-result.json from project root"
  cp jmh-result.json "$OUTPUT_DIR/jmh-result.json"
else
  # Find the result file in benchmark-results directory
  echo "Looking for JMH result files in benchmark-results directory"
  if [ -d "$BENCHMARK_RESULTS_DIR" ]; then
    find "$BENCHMARK_RESULTS_DIR" -name "jmh-result*.json" -type f -exec cp {} "$OUTPUT_DIR/jmh-result.json" \; 2>/dev/null || true
  fi
fi

# Verify benchmark result file exists
if [ ! -f "$OUTPUT_DIR/jmh-result.json" ]; then
  echo "ERROR: No benchmark result file found!"
  exit 1
fi

# Copy the JMH Visualizer template
cp "$TEMPLATES_DIR/index-visualizer.html" "$OUTPUT_DIR/index.html"
echo "Copied JMH Visualizer template"

# Copy step metrics visualizer if available
if [ -f "$TEMPLATES_DIR/step-metrics-visualizer.html" ]; then
  cp "$TEMPLATES_DIR/step-metrics-visualizer.html" "$OUTPUT_DIR/step-metrics.html"
  echo "Copied step metrics visualizer template"
fi

# Create directory for badges
mkdir -p "$OUTPUT_DIR/badges"

echo "GitHub Pages structure prepared successfully"