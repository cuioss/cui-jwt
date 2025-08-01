#!/bin/bash
# Update consolidated performance tracking and calculate trends
# Usage: update-performance-trends.sh <templates-dir> <output-dir> <commit-hash> <score> <throughput> <latency> <resilience>

set -e

TEMPLATES_DIR="$1"
OUTPUT_DIR="$2"
COMMIT_HASH="$3"
CURRENT_SCORE="$4"
CURRENT_THROUGHPUT="$5"
CURRENT_LATENCY="$6"
CURRENT_RESILIENCE="$7"

echo "Updating consolidated performance tracking..."

# Download existing tracking file if it exists
TRACKING_FILE="$OUTPUT_DIR/data/performance-tracking.json"
curl -f -s "https://cuioss.github.io/cui-jwt/benchmarks/data/performance-tracking.json" -o "$TRACKING_FILE" 2>/dev/null || echo '{"runs":[]}' > "$TRACKING_FILE"

# Add current run to tracking data
CURRENT_RUN=$(cat <<EOF
{
  "timestamp": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
  "commit": "$COMMIT_HASH",
  "performance": {
    "score": $CURRENT_SCORE,
    "throughput": {"value": $CURRENT_THROUGHPUT, "unit": "ops/s"},
    "averageTime": {"value": $CURRENT_LATENCY, "unit": "s"},
    "errorResilience": {"value": $CURRENT_RESILIENCE, "unit": "ops/s"}
  }
}
EOF
)

# Validate JSON structure before updating
if jq empty "$TRACKING_FILE" 2>/dev/null; then
  # Add new run and keep only last 10 runs
  jq --argjson newrun "$CURRENT_RUN" '.runs += [$newrun] | .runs = (.runs | sort_by(.timestamp) | .[-10:])' "$TRACKING_FILE" > "$TRACKING_FILE.tmp" && mv "$TRACKING_FILE.tmp" "$TRACKING_FILE"
else
  echo "Warning: Invalid JSON in tracking file, resetting to default structure."
  echo '{"runs":[]}' > "$TRACKING_FILE"
  jq --argjson newrun "$CURRENT_RUN" '.runs += [$newrun] | .runs = (.runs | sort_by(.timestamp) | .[-10:])' "$TRACKING_FILE" > "$TRACKING_FILE.tmp" && mv "$TRACKING_FILE.tmp" "$TRACKING_FILE"
fi

# Calculate trends and create trend badge using unified script
bash "$TEMPLATES_DIR/scripts/create-unified-trend-badge.sh" micro "$TRACKING_FILE" "$OUTPUT_DIR/badges"

# Performance trends template is copied by prepare-github-pages.sh script

echo "Performance tracking updated successfully"
