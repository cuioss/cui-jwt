#!/bin/bash
# Prepare step metrics visualization
# Usage: prepare-step-metrics.sh <benchmark-results-dir> <templates-dir> <output-dir>

set -e

BENCHMARK_RESULTS_DIR="$1"
TEMPLATES_DIR="$2"
OUTPUT_DIR="$3"

echo "Preparing step metrics visualization..."

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Copy step metrics data if it exists
if [ -f "$BENCHMARK_RESULTS_DIR/jwt-validation-metrics.json" ]; then
  cp "$BENCHMARK_RESULTS_DIR/jwt-validation-metrics.json" "$OUTPUT_DIR/"
  echo "Copied jwt-validation-metrics.json"
else
  echo "Warning: jwt-validation-metrics.json not found in $BENCHMARK_RESULTS_DIR"
fi

# Copy step metrics visualizer template
if [ -f "$TEMPLATES_DIR/step-metrics-visualizer.html" ]; then
  cp "$TEMPLATES_DIR/step-metrics-visualizer.html" "$OUTPUT_DIR/step-metrics.html"
  echo "Copied step metrics visualizer template"
else
  echo "Warning: step-metrics-visualizer.html template not found"
fi

echo "Step metrics visualization prepared successfully"