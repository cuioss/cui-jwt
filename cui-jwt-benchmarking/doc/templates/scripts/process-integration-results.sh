#!/bin/bash

# Integration Benchmark Result Processing Script
# Processes JMH integration benchmark results and creates visualization data
# Usage: ./process-integration-results.sh <integration-result.json> <output-dir> <commit-hash>

set -euo pipefail

INTEGRATION_RESULT="$1"
OUTPUT_DIR="$2"
COMMIT_HASH="$3"

if [ ! -f "$INTEGRATION_RESULT" ]; then
    echo "Error: Integration result file not found: $INTEGRATION_RESULT"
    exit 1
fi

# Create integration output directory
mkdir -p "$OUTPUT_DIR/integration"
mkdir -p "$OUTPUT_DIR/integration/badges"

echo "Processing integration benchmark results from: $INTEGRATION_RESULT"

# Extract key integration metrics
TOTAL_BENCHMARKS=$(jq '[.[] | select(.benchmark)] | length' "$INTEGRATION_RESULT")
THROUGHPUT_BENCHMARKS=$(jq '[.[] | select(.benchmark and (.mode == "thrpt" or .primaryMetric.score > 0))] | length' "$INTEGRATION_RESULT")

# Calculate average integration throughput (requests/second)
AVG_INTEGRATION_THROUGHPUT=$(jq -r '
  [.[] | select(.benchmark and (.mode == "thrpt" or .primaryMetric.scoreUnit == "ops/s"))] |
  if length > 0 then
    (map(.primaryMetric.score) | add / length | . * 100 | round / 100)
  else
    0
  end
' "$INTEGRATION_RESULT")

# Calculate average integration latency (seconds)
AVG_INTEGRATION_LATENCY=$(jq -r '
  [.[] | select(.benchmark and (.mode == "avgt" or .primaryMetric.scoreUnit == "ms/op"))] |
  if length > 0 then
    # Convert milliseconds to seconds
    (map(.primaryMetric.score) | add / length | . / 1000 | . * 1000000 | round / 1000000)
  else
    0
  end
' "$INTEGRATION_RESULT")

# Calculate integration performance score (weighted combination)
INTEGRATION_SCORE=$(echo "$AVG_INTEGRATION_THROUGHPUT $AVG_INTEGRATION_LATENCY" | awk '{
  throughput = $1
  latency = $2
  if (throughput > 0 && latency > 0) {
    # Score = (throughput * 0.57) + (latency_inverted * 0.40) + (error_resilience * 0.03)
    # Using latency in seconds, convert to operations per second equivalent
    latency_inverted = 1 / latency
    # Error resilience assumed as 0 for integration tests (no error injection)
    error_resilience = 0
    score = (throughput * 0.57) + (latency_inverted * 0.40) + (error_resilience * 0.03)
    printf "%.2f", score
  } else if (throughput > 0) {
    printf "%.2f", throughput * 0.57
  } else {
    print "0.00"
  }
}')

echo "Integration Metrics:"
echo "  Total Benchmarks: $TOTAL_BENCHMARKS"
echo "  Avg Throughput: $AVG_INTEGRATION_THROUGHPUT ops/s"
echo "  Avg Latency: $AVG_INTEGRATION_LATENCY s"
echo "  Integration Score: $INTEGRATION_SCORE"

# Create integration performance badge
BADGE_COLOR="red"
BADGE_MESSAGE="$INTEGRATION_SCORE"

if (( $(echo "$INTEGRATION_SCORE >= 50" | bc -l) )); then
    BADGE_COLOR="green"
elif (( $(echo "$INTEGRATION_SCORE >= 25" | bc -l) )); then
    BADGE_COLOR="yellow"
elif (( $(echo "$INTEGRATION_SCORE >= 10" | bc -l) )); then
    BADGE_COLOR="orange"
fi

cat > "$OUTPUT_DIR/integration/badges/performance-badge.json" << EOF
{
  "schemaVersion": 1,
  "label": "Integration Performance",
  "message": "$BADGE_MESSAGE",
  "color": "$BADGE_COLOR"
}
EOF

# Create integration throughput badge
THROUGHPUT_COLOR="red"
if (( $(echo "$AVG_INTEGRATION_THROUGHPUT >= 100" | bc -l) )); then
    THROUGHPUT_COLOR="green"
elif (( $(echo "$AVG_INTEGRATION_THROUGHPUT >= 50" | bc -l) )); then
    THROUGHPUT_COLOR="yellow"
elif (( $(echo "$AVG_INTEGRATION_THROUGHPUT >= 25" | bc -l) )); then
    THROUGHPUT_COLOR="orange"
fi

cat > "$OUTPUT_DIR/integration/badges/throughput-badge.json" << EOF
{
  "schemaVersion": 1,
  "label": "Integration Throughput",
  "message": "$AVG_INTEGRATION_THROUGHPUT ops/s",
  "color": "$THROUGHPUT_COLOR"
}
EOF

# Create integration latency badge
LATENCY_COLOR="red"
if (( $(echo "$AVG_INTEGRATION_LATENCY <= 0.01" | bc -l) )); then
    LATENCY_COLOR="green"
elif (( $(echo "$AVG_INTEGRATION_LATENCY <= 0.025" | bc -l) )); then
    LATENCY_COLOR="yellow"
elif (( $(echo "$AVG_INTEGRATION_LATENCY <= 0.05" | bc -l) )); then
    LATENCY_COLOR="orange"
fi

cat > "$OUTPUT_DIR/integration/badges/latency-badge.json" << EOF
{
  "schemaVersion": 1,
  "label": "Integration Latency",
  "message": "$AVG_INTEGRATION_LATENCY s",
  "color": "$LATENCY_COLOR"
}
EOF

# Create detailed integration metrics file
cat > "$OUTPUT_DIR/integration/metrics.json" << EOF
{
  "timestamp": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
  "commit": "$COMMIT_HASH",
  "integration": {
    "totalBenchmarks": $TOTAL_BENCHMARKS,
    "throughputBenchmarks": $THROUGHPUT_BENCHMARKS,
    "avgThroughput": $AVG_INTEGRATION_THROUGHPUT,
    "avgLatency": $AVG_INTEGRATION_LATENCY,
    "performanceScore": $INTEGRATION_SCORE
  }
}
EOF

echo "Integration badges and metrics created in: $OUTPUT_DIR/integration/"

# Output metrics for GitHub Actions workflow
echo "INTEGRATION_SCORE=$INTEGRATION_SCORE"
echo "INTEGRATION_THROUGHPUT=$AVG_INTEGRATION_THROUGHPUT"
echo "INTEGRATION_LATENCY=$AVG_INTEGRATION_LATENCY"
