#!/bin/bash
# Process integration JMH benchmark results
# Usage: process-integration-jmh-benchmarks.sh <integration-benchmark-result.json> <output-dir> <commit-hash> <timestamp>

set -e

INTEGRATION_JMH_RESULTS="$1"
OUTPUT_DIR="$2"
COMMIT_HASH="$3"
TIMESTAMP="$4"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TEMPLATES_DIR="$(dirname "$SCRIPT_DIR")/doc/templates"

echo "🔗 Processing integration JMH benchmark results..."
echo "📊 Integration JMH Results: $INTEGRATION_JMH_RESULTS"
echo "📦 Output Directory: $OUTPUT_DIR"

# Validate input file
if [ ! -f "$INTEGRATION_JMH_RESULTS" ]; then
    echo "❌ Error: Integration JMH results not found: $INTEGRATION_JMH_RESULTS"
    exit 1
fi

# Ensure output directories exist
mkdir -p "$OUTPUT_DIR/data"
mkdir -p "$OUTPUT_DIR/badges"

# Copy the integration benchmark results to the data directory
echo "📋 Copying integration benchmark results..."
cp "$INTEGRATION_JMH_RESULTS" "$OUTPUT_DIR/data/integration-benchmark-result.json"

# Also copy as jmh-result.json for fallback compatibility
cp "$INTEGRATION_JMH_RESULTS" "$OUTPUT_DIR/data/jmh-result.json"

# Copy the integration visualizer template
echo "📄 Copying integration visualizer template..."
if [ -f "$TEMPLATES_DIR/integration-benchmark-visualizer.html" ]; then
    cp "$TEMPLATES_DIR/integration-benchmark-visualizer.html" "$OUTPUT_DIR/integration-benchmark-visualizer.html"
else
    echo "⚠️  Warning: Integration visualizer template not found, using fallback"
    # Create a minimal fallback if template doesn't exist
    cat > "$OUTPUT_DIR/integration-benchmark-visualizer.html" << 'EOF'
<!DOCTYPE html>
<html>
<head>
    <title>Integration Benchmark Results</title>
    <meta http-equiv="refresh" content="0; url=index-visualizer.html">
</head>
<body>
    <p>Redirecting to benchmark results...</p>
</body>
</html>
EOF
fi

# Extract key metrics from JMH results for badges
echo "🎯 Extracting integration metrics for badges..."

# Extract health check and JWT validation throughput
HEALTH_THROUGHPUT=$(jq -r '[.[] | select(.benchmark | contains("healthCheck")) | .primaryMetric.score] | if length > 0 then (add / length) else 0 end' "$INTEGRATION_JMH_RESULTS" 2>/dev/null || echo "0")
JWT_THROUGHPUT=$(jq -r '[.[] | select(.benchmark | contains("jwt") or contains("Jwt") or contains("validation")) | .primaryMetric.score] | if length > 0 then (add / length) else 0 end' "$INTEGRATION_JMH_RESULTS" 2>/dev/null || echo "0")

# Format throughput values
if [ "$HEALTH_THROUGHPUT" != "0" ]; then
    HEALTH_BADGE_VALUE=$(printf "%.1f ops/s" "$HEALTH_THROUGHPUT")
    HEALTH_COLOR="green"
else
    HEALTH_BADGE_VALUE="No Data"
    HEALTH_COLOR="red"
fi

if [ "$JWT_THROUGHPUT" != "0" ]; then
    JWT_BADGE_VALUE=$(printf "%.1f ops/s" "$JWT_THROUGHPUT")
    JWT_COLOR="green"
else
    JWT_BADGE_VALUE="No Data"
    JWT_COLOR="red"
fi

# Create integration performance badges (using standard names that match fallback badges)
echo "{\"schemaVersion\":1,\"label\":\"Health Check\",\"message\":\"$HEALTH_BADGE_VALUE\",\"color\":\"$HEALTH_COLOR\"}" > "$OUTPUT_DIR/badges/integration-health-badge.json"
echo "{\"schemaVersion\":1,\"label\":\"JWT Validation\",\"message\":\"$JWT_BADGE_VALUE\",\"color\":\"$JWT_COLOR\"}" > "$OUTPUT_DIR/badges/integration-jwt-badge.json"

# Create main integration performance badge (replaces fallback)
if [ "$HEALTH_THROUGHPUT" != "0" ] && [ "$JWT_THROUGHPUT" != "0" ]; then
    SUMMARY_VALUE="✓ Running"
    SUMMARY_COLOR="green"
else
    SUMMARY_VALUE="⚠ Partial"
    SUMMARY_COLOR="yellow"
fi
echo "{\"schemaVersion\":1,\"label\":\"Integration Performance\",\"message\":\"$SUMMARY_VALUE\",\"color\":\"$SUMMARY_COLOR\"}" > "$OUTPUT_DIR/badges/integration-performance-badge.json"

# Create integration trend badge (replaces fallback)
TREND_VALUE="↗ Active"
echo "{\"schemaVersion\":1,\"label\":\"Integration Trend\",\"message\":\"$TREND_VALUE\",\"color\":\"green\"}" > "$OUTPUT_DIR/badges/integration-trend-badge.json"

# Create metadata file
cat > "$OUTPUT_DIR/data/integration-metadata.json" << EOF
{
  "timestamp": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
  "commit": "$COMMIT_HASH",
  "generated_at": "$TIMESTAMP",
  "metrics": {
    "health_check_throughput": $HEALTH_THROUGHPUT,
    "jwt_validation_throughput": $JWT_THROUGHPUT
  }
}
EOF

echo "✅ Integration JMH benchmarks processed successfully"
echo "   - Health Check: $HEALTH_BADGE_VALUE"
echo "   - JWT Validation: $JWT_BADGE_VALUE"