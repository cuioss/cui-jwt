#!/bin/bash
# Unified Performance Badge Creation Script
# Handles both micro-benchmarks and integration benchmarks
# Usage: create-unified-performance-badge.sh <benchmark-type> <result-file> <output-directory> [commit-hash]

set -e

BENCHMARK_TYPE="$1"  # "micro" or "integration"
RESULT_FILE="$2"
OUTPUT_DIR="$3"
COMMIT_HASH="${4:-unknown}"

if [ ! -f "$RESULT_FILE" ]; then
  echo "Error: Result file not found: $RESULT_FILE"
  exit 1
fi

# Create output directory
mkdir -p "$OUTPUT_DIR"

echo "Creating unified performance badge for $BENCHMARK_TYPE benchmarks..."

# Common badge creation function
create_badge() {
    local label="$1"
    local message="$2"
    local color="$3"
    local filename="$4"
    
    echo "{\"schemaVersion\":1,\"label\":\"$label\",\"message\":\"$message\",\"color\":\"$color\"}" > "$OUTPUT_DIR/$filename"
}

# Common unit conversion functions
convert_to_ops_per_sec() {
    local value="$1"
    local unit="$2"
    
    if [[ "$unit" == "ops/s" ]]; then
        echo "$value"
    elif [[ "$unit" == "s/op" ]]; then
        if [ "$(echo "$value == 0" | bc -l)" -eq 1 ]; then
            echo "0"
        else
            echo "scale=2; 1 / $value" | bc -l
        fi
    elif [[ "$unit" == "ms/op" ]]; then
        if [ "$(echo "$value == 0" | bc -l)" -eq 1 ]; then
            echo "0"
        else
            echo "scale=2; 1000 / $value" | bc -l
        fi
    elif [[ "$unit" == "us/op" ]]; then
        if [ "$(echo "$value == 0" | bc -l)" -eq 1 ]; then
            echo "0"
        else
            echo "scale=2; 1000000 / $value" | bc -l
        fi
    else
        echo "0"
    fi
}

convert_to_milliseconds() {
    local value="$1"
    local unit="$2"
    
    if [[ "$unit" == "ms/op" ]]; then
        echo "$value"
    elif [[ "$unit" == "us/op" ]]; then
        echo "scale=3; $value / 1000" | bc -l
    elif [[ "$unit" == "s/op" ]]; then
        echo "scale=3; $value * 1000" | bc -l
    else
        echo "0"
    fi
}

format_throughput_display() {
    local throughput="$1"
    
    if [ $(echo "$throughput >= 1000" | bc -l) -eq 1 ]; then
        throughput_k=$(echo "scale=1; $throughput / 1000" | bc -l)
        echo "${throughput_k}k"
    else
        printf "%.0f" "$throughput"
    fi
}

# Calculate weighted performance score
calculate_performance_score() {
    local throughput="$1"
    local latency_ms="$2"
    local error_resilience="$3"
    
    # Convert latency to operations per second equivalent
    local latency_ops_per_sec
    if [ "$(echo "$latency_ms == 0" | bc -l)" -eq 1 ]; then
        latency_ops_per_sec="0"
    else
        latency_ops_per_sec=$(echo "scale=2; 1000 / $latency_ms" | bc -l)
    fi
    
    # Calculate weighted score
    if [ "$error_resilience" != "0" ]; then
        # Enhanced formula: (throughput * 0.57) + (latency * 0.40) + (error_resilience * 0.03)
        echo "scale=2; ($throughput * 0.57) + ($latency_ops_per_sec * 0.40) + ($error_resilience * 0.03)" | bc -l
    else
        # Fallback to original formula: (throughput * 0.6) + (latency * 0.4)
        echo "scale=2; ($throughput * 0.6) + ($latency_ops_per_sec * 0.4)" | bc -l
    fi
}

# Process micro benchmarks
process_micro_benchmarks() {
    echo "Processing micro-benchmark results..."
    
    # Extract throughput data
    local throughput_entry=$(jq -r '.[] | select(.benchmark == "de.cuioss.jwt.validation.benchmark.PerformanceIndicatorBenchmark.measureThroughput")' "$RESULT_FILE" 2>/dev/null)
    local throughput_score=$(echo "$throughput_entry" | jq -r '.primaryMetric.score' 2>/dev/null || echo "0")
    local throughput_unit=$(echo "$throughput_entry" | jq -r '.primaryMetric.scoreUnit' 2>/dev/null || echo "")
    
    # Extract average time data
    local avg_time_entry=$(jq -r '.[] | select(.benchmark == "de.cuioss.jwt.validation.benchmark.PerformanceIndicatorBenchmark.measureAverageTime")' "$RESULT_FILE" 2>/dev/null)
    local avg_time_score=$(echo "$avg_time_entry" | jq -r '.primaryMetric.score' 2>/dev/null || echo "0")
    local avg_time_unit=$(echo "$avg_time_entry" | jq -r '.primaryMetric.scoreUnit' 2>/dev/null || echo "")
    
    # Extract error resilience data
    local error_resilience_entry=$(jq -r '.[] | select(.benchmark == "de.cuioss.jwt.validation.benchmark.ErrorLoadBenchmark.validateMixedTokens" and .params.errorPercentage == "0")' "$RESULT_FILE" 2>/dev/null)
    local error_resilience_score=$(echo "$error_resilience_entry" | jq -r '.primaryMetric.score' 2>/dev/null || echo "0")
    local error_resilience_unit=$(echo "$error_resilience_entry" | jq -r '.primaryMetric.scoreUnit' 2>/dev/null || echo "")
    
    # Convert units
    local throughput_ops_per_sec=$(convert_to_ops_per_sec "$throughput_score" "$throughput_unit")
    local avg_time_ms=$(convert_to_milliseconds "$avg_time_score" "$avg_time_unit")
    local error_resilience_ops_per_sec=$(convert_to_ops_per_sec "$error_resilience_score" "$error_resilience_unit")
    
    # Calculate performance score
    local performance_score=$(calculate_performance_score "$throughput_ops_per_sec" "$avg_time_ms" "$error_resilience_ops_per_sec")
    
    # Format for display
    local formatted_score=$(printf "%.0f" "$performance_score")
    local throughput_display=$(format_throughput_display "$throughput_ops_per_sec")
    local formatted_avg_time_ms=$(echo "$avg_time_ms" | sed 's/0*$//' | sed 's/\.$//')
    
    # Create performance badge
    create_badge "Performance Score" "${formatted_score} (${throughput_display} ops/s, ${formatted_avg_time_ms}ms)" "brightgreen" "performance-badge.json"
    
    echo "Created micro-benchmark performance badge: Score=$formatted_score (Throughput=${throughput_display} ops/s, AvgTime=${formatted_avg_time_ms}ms)"
}

# Process integration benchmarks
process_integration_benchmarks() {
    echo "Processing integration benchmark results..."
    
    # Calculate average integration throughput
    local avg_throughput=$(jq -r '
      [.[] | select(.benchmark and (.mode == "thrpt" or .primaryMetric.scoreUnit == "ops/s"))] |
      if length > 0 then
        (map(.primaryMetric.score) | add / length | . * 100 | round / 100)
      else
        0
      end
    ' "$RESULT_FILE")
    
    # Calculate average integration latency (convert to milliseconds)
    local avg_latency_ms=$(jq -r '
      [.[] | select(.benchmark and (.mode == "avgt" or .primaryMetric.scoreUnit == "ms/op"))] |
      if length > 0 then
        (map(.primaryMetric.score) | add / length | . * 1000 | round / 1000)
      else
        0
      end
    ' "$RESULT_FILE")
    
    # Calculate integration performance score
    local integration_score=$(calculate_performance_score "$avg_throughput" "$avg_latency_ms" "0")
    
    # Format for display
    local formatted_score=$(printf "%.0f" "$integration_score")
    local throughput_display=$(format_throughput_display "$avg_throughput")
    local formatted_latency_ms=$(echo "$avg_latency_ms" | sed 's/0*$//' | sed 's/\.$//')
    
    # Determine badge color
    local badge_color="red"
    if (( $(echo "$integration_score >= 50" | bc -l) )); then
        badge_color="green"
    elif (( $(echo "$integration_score >= 25" | bc -l) )); then
        badge_color="yellow"
    elif (( $(echo "$integration_score >= 10" | bc -l) )); then
        badge_color="orange"
    fi
    
    # Create performance badge
    create_badge "Performance Score" "${formatted_score} (${throughput_display} ops/s, ${formatted_latency_ms}ms)" "$badge_color" "performance-badge.json"
    
    # Create additional integration-specific badges
    local throughput_color="red"
    if (( $(echo "$avg_throughput >= 100" | bc -l) )); then
        throughput_color="green"
    elif (( $(echo "$avg_throughput >= 50" | bc -l) )); then
        throughput_color="yellow"
    elif (( $(echo "$avg_throughput >= 25" | bc -l) )); then
        throughput_color="orange"
    fi
    
    local latency_color="red"
    if (( $(echo "$avg_latency_ms <= 10" | bc -l) )); then
        latency_color="green"
    elif (( $(echo "$avg_latency_ms <= 25" | bc -l) )); then
        latency_color="yellow"
    elif (( $(echo "$avg_latency_ms <= 50" | bc -l) )); then
        latency_color="orange"
    fi
    
    create_badge "Throughput" "${throughput_display} ops/s" "$throughput_color" "throughput-badge.json"
    create_badge "Latency" "${formatted_latency_ms}ms" "$latency_color" "latency-badge.json"
    
    echo "Created integration benchmark badges: Score=$formatted_score (Throughput=${throughput_display} ops/s, Latency=${formatted_latency_ms}ms)"
}

# Main processing logic
if [ "$BENCHMARK_TYPE" = "micro" ]; then
    process_micro_benchmarks
elif [ "$BENCHMARK_TYPE" = "integration" ]; then
    process_integration_benchmarks
else
    echo "Error: Invalid benchmark type. Use 'micro' or 'integration'"
    exit 1
fi

echo "Badge creation completed successfully"