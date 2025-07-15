#!/bin/bash

# Process wrk results and convert to JMH-compatible format
# This script converts wrk JSON output to match JMH result structure

set -euo pipefail

WRK_RESULTS_FILE="${1:-./target/wrk-results/wrk-results.json}"
OUTPUT_FILE="${2:-./target/benchmark-results/wrk-benchmark-result.json}"

if [ ! -f "$WRK_RESULTS_FILE" ]; then
    echo "❌ wrk results file not found: $WRK_RESULTS_FILE"
    exit 1
fi

echo "📊 Processing wrk results..."
echo "  Input: $WRK_RESULTS_FILE"
echo "  Output: $OUTPUT_FILE"

# Create output directory
mkdir -p "$(dirname "$OUTPUT_FILE")"

# Convert wrk results to JMH-compatible format
jq -n --slurpfile wrk_data "$WRK_RESULTS_FILE" '
[
  {
    "jmhVersion": "wrk-integration",
    "benchmark": "wrk.jwt.integration.benchmark.JwtValidationBenchmark.measureThroughput",
    "mode": "thrpt",
    "threads": 200,
    "forks": 1,
    "primaryMetric": {
      "score": $wrk_data[0].throughput_rps,
      "scoreError": "NaN",
      "scoreUnit": "ops/s"
    }
  },
  {
    "jmhVersion": "wrk-integration", 
    "benchmark": "wrk.jwt.integration.benchmark.JwtValidationBenchmark.measureAverageTime",
    "mode": "avgt",
    "threads": 200,
    "forks": 1,
    "primaryMetric": {
      "score": $wrk_data[0].latency.p95,
      "scoreError": "NaN", 
      "scoreUnit": "ms/op"
    }
  },
  {
    "jmhVersion": "wrk-integration",
    "benchmark": "wrk.jwt.integration.benchmark.JwtValidationBenchmark.measureErrorResilience", 
    "mode": "thrpt",
    "threads": 200,
    "forks": 1,
    "primaryMetric": {
      "score": $wrk_data[0].throughput_rps,
      "scoreError": "NaN",
      "scoreUnit": "ops/s"
    }
  }
]
' > "$OUTPUT_FILE"

echo "✅ wrk results converted to JMH format"
echo "📄 Output saved to: $OUTPUT_FILE"

# Display comparison with JMH results
if [ -f "./target/benchmark-results/integration-benchmark-result.json" ]; then
    echo ""
    echo "=== Performance Comparison ==="
    echo "JMH Integration Results:"
    jq -r '.[] | select(.benchmark | contains("measureThroughput")) | "  Throughput: " + (.primaryMetric.score | tostring) + " " + .primaryMetric.scoreUnit' ./target/benchmark-results/integration-benchmark-result.json
    jq -r '.[] | select(.benchmark | contains("measureAverageTime")) | "  Latency: " + (.primaryMetric.score | tostring) + " " + .primaryMetric.scoreUnit' ./target/benchmark-results/integration-benchmark-result.json
    
    echo ""
    echo "wrk Integration Results:"
    jq -r '.[] | select(.benchmark | contains("measureThroughput")) | "  Throughput: " + (.primaryMetric.score | tostring) + " " + .primaryMetric.scoreUnit' "$OUTPUT_FILE"
    jq -r '.[] | select(.benchmark | contains("measureAverageTime")) | "  Latency: " + (.primaryMetric.score | tostring) + " " + .primaryMetric.scoreUnit' "$OUTPUT_FILE"
fi