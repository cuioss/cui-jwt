#!/bin/bash
# Create comprehensive performance badge from JMH benchmark results
# Usage: create-performance-badge.sh <jmh-result-file> <output-directory>

set -e

JMH_RESULT_FILE="$1"
OUTPUT_DIR="$2"

if [ ! -f "$JMH_RESULT_FILE" ]; then
  echo "Error: JMH result file not found: $JMH_RESULT_FILE"
  exit 1
fi

echo "Creating performance indicator badge..."

# Check benchmark type first
BENCHMARK_TYPE="micro"
if grep -q "JwtHealthBenchmark\|JwtValidationBenchmark\|JwtEchoBenchmark" "$JMH_RESULT_FILE"; then
    BENCHMARK_TYPE="integration"
fi

if [ "$BENCHMARK_TYPE" == "integration" ]; then
    # For integration benchmarks, delegate to unified script
    echo "Detected integration benchmarks, using unified badge creation..."
    SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
    exec bash "$SCRIPT_DIR/create-unified-performance-badge.sh" integration "$JMH_RESULT_FILE" "$OUTPUT_DIR"
fi

# Extract throughput data from SimpleCoreValidationBenchmark.measureThroughput
throughput_entry=$(jq -r '.[] | select(.benchmark == "de.cuioss.jwt.validation.benchmark.standard.SimpleCoreValidationBenchmark.measureThroughput")' "$JMH_RESULT_FILE" 2>/dev/null)
throughput_score=$(echo "$throughput_entry" | jq -r '.primaryMetric.score' 2>/dev/null || echo "0")
throughput_unit=$(echo "$throughput_entry" | jq -r '.primaryMetric.scoreUnit' 2>/dev/null || echo "")

# Extract average time data from SimpleCoreValidationBenchmark.measureAverageTime  
avg_time_entry=$(jq -r '.[] | select(.benchmark == "de.cuioss.jwt.validation.benchmark.standard.SimpleCoreValidationBenchmark.measureAverageTime")' "$JMH_RESULT_FILE" 2>/dev/null)
avg_time_score=$(echo "$avg_time_entry" | jq -r '.primaryMetric.score' 2>/dev/null || echo "0")
avg_time_unit=$(echo "$avg_time_entry" | jq -r '.primaryMetric.scoreUnit' 2>/dev/null || echo "")

# Extract error resilience data from SimpleErrorLoadBenchmark with 0% error (validateMixedTokens0)
error_resilience_entry=$(jq -r '.[] | select(.benchmark == "de.cuioss.jwt.validation.benchmark.standard.SimpleErrorLoadBenchmark.validateMixedTokens0")' "$JMH_RESULT_FILE" 2>/dev/null)
error_resilience_score=$(echo "$error_resilience_entry" | jq -r '.primaryMetric.score' 2>/dev/null || echo "0")
error_resilience_unit=$(echo "$error_resilience_entry" | jq -r '.primaryMetric.scoreUnit' 2>/dev/null || echo "")

echo "Debug: Throughput: $throughput_score $throughput_unit, AvgTime: $avg_time_score $avg_time_unit, ErrorResilience: $error_resilience_score $error_resilience_unit"

if [ "$throughput_score" != "0" ] && [ "$throughput_score" != "null" ] && [ "$avg_time_score" != "0" ] && [ "$avg_time_score" != "null" ]; then
  # Convert throughput to ops/sec if needed
  if [[ "$throughput_unit" == "ops/s" ]]; then
    throughput_ops_per_sec=$(echo "$throughput_score" | awk '{printf "%.0f", $1}')
  elif [[ "$throughput_unit" == "s/op" ]]; then
    # Convert s/op to ops/s
    throughput_ops_per_sec=$(if [ "$(echo "$throughput_score == 0" | bc -l)" -eq 1 ]; then echo "0"; else echo "scale=0; 1 / $throughput_score"; fi | bc -l)
  else
    echo "Warning: Unknown throughput unit: $throughput_unit"
    throughput_ops_per_sec="0"
  fi

  # Convert avg time to microseconds if needed
  if [[ "$avg_time_unit" == "us/op" ]]; then
    avg_time_microseconds=$(echo "$avg_time_score" | awk '{printf "%.1f", $1}')
  elif [[ "$avg_time_unit" == "ms/op" ]]; then
    # Convert ms to microseconds
    avg_time_microseconds=$(echo "scale=1; $avg_time_score * 1000" | bc -l)
  elif [[ "$avg_time_unit" == "s/op" ]]; then
    # Convert s to microseconds
    avg_time_microseconds=$(echo "scale=1; $avg_time_score * 1000000" | bc -l)
  else
    echo "Warning: Unknown avg time unit: $avg_time_unit"
    avg_time_microseconds="0"
  fi

  # Convert error resilience to ops/sec if needed
  if [ "$error_resilience_score" != "0" ] && [ "$error_resilience_score" != "null" ]; then
    if [[ "$error_resilience_unit" == "ops/s" ]]; then
      error_resilience_ops_per_sec=$(echo "$error_resilience_score" | awk '{printf "%.0f", $1}')
    elif [[ "$error_resilience_unit" == "ms/op" ]]; then
      # Convert ms/op to ops/s
      error_resilience_ops_per_sec=$(if [ "$(echo "$error_resilience_score == 0" | bc -l)" -eq 1 ]; then echo "0"; else echo "scale=0; 1000 / $error_resilience_score"; fi | bc -l)
    elif [[ "$error_resilience_unit" == "us/op" ]]; then
      # Convert us/op to ops/s
      error_resilience_ops_per_sec=$(if [ "$(echo "$error_resilience_score == 0" | bc -l)" -eq 1 ]; then echo "0"; else echo "scale=0; 1000000 / $error_resilience_score"; fi | bc -l)
    else
      echo "Warning: Unknown error resilience unit: $error_resilience_unit"
      error_resilience_ops_per_sec="0"
    fi
  else
    error_resilience_ops_per_sec="0"
  fi

  echo "Debug: Converted - Throughput: ${throughput_ops_per_sec} ops/s, AvgTime: ${avg_time_microseconds} μs, ErrorResilience: ${error_resilience_ops_per_sec} ops/s"

  if [ "$throughput_ops_per_sec" != "0" ] && [ "$avg_time_microseconds" != "0" ]; then
    # Calculate latency component
    latency_ops_per_sec=$(if [ "$(echo "$avg_time_microseconds == 0" | bc -l)" -eq 1 ]; then echo "0"; else echo "scale=2; 1000000 / $avg_time_microseconds"; fi | bc -l)

    # Calculate comprehensive weighted performance score with error resilience
    if [ "$error_resilience_ops_per_sec" != "0" ]; then
      # Enhanced formula: (throughput * 0.57) + (latency * 0.40) + (error_resilience * 0.03)
      performance_score=$(echo "scale=2; ($throughput_ops_per_sec * 0.57) + ($latency_ops_per_sec * 0.40) + ($error_resilience_ops_per_sec * 0.03)" | bc -l)
      echo "Debug: Using enhanced scoring with error resilience"
    else
      # Fallback to original formula: (throughput * 0.6) + (latency * 0.4)
      performance_score=$(echo "scale=2; ($throughput_ops_per_sec * 0.6) + ($latency_ops_per_sec * 0.4)" | bc -l)
      echo "Debug: Using original scoring (no error resilience data)"
    fi

    formatted_score=$(printf "%.0f" $performance_score)

    # Format throughput with k for thousands
    if [ $(echo "$throughput_ops_per_sec >= 1000" | bc -l) -eq 1 ]; then
      throughput_k=$(echo "scale=1; $throughput_ops_per_sec / 1000" | bc -l)
      throughput_display="${throughput_k}k"
    else
      throughput_display=$(printf "%.0f" $throughput_ops_per_sec)
    fi

    # Convert microseconds to milliseconds for display
    avg_time_ms=$(echo "scale=3; $avg_time_microseconds / 1000" | bc -l)
    # Round to whole number for milliseconds
    formatted_avg_time_ms=$(printf "%.0f" "$avg_time_ms")

    # Create badge with performance score (normalized to milliseconds)
    echo "{\"schemaVersion\":1,\"label\":\"Performance Score\",\"message\":\"${formatted_score} (${throughput_display} ops/s, ${formatted_avg_time_ms}ms)\",\"color\":\"brightgreen\"}" > "$OUTPUT_DIR/performance-badge.json"

    echo "Created performance badge: Score=$formatted_score (Throughput=${throughput_display} ops/s, AvgTime=${formatted_avg_time_ms}ms)"

    # Export metrics for use by other scripts
    echo "PERFORMANCE_SCORE=$formatted_score"
    echo "THROUGHPUT_OPS_PER_SEC=$throughput_ops_per_sec"
    echo "THROUGHPUT_DISPLAY=$throughput_display"
    echo "AVERAGE_TIME_MS=$formatted_avg_time_ms"
    echo "ERROR_RESILIENCE_OPS_PER_SEC=$error_resilience_ops_per_sec"
    echo "AVG_TIME_MICROS=$avg_time_microseconds"
  else
    echo "Warning: Unit conversion failed"
    echo "{\"schemaVersion\":1,\"label\":\"Performance Score\",\"message\":\"Conversion Error\",\"color\":\"yellow\"}" > "$OUTPUT_DIR/performance-badge.json"
    exit 1
  fi
else
  echo "Warning: Could not extract valid performance metrics (throughput=$throughput_score, avg_time=$avg_time_score)"
  echo "{\"schemaVersion\":1,\"label\":\"Performance Score\",\"message\":\"Pending\",\"color\":\"yellow\"}" > "$OUTPUT_DIR/performance-badge.json"
  exit 1
fi
