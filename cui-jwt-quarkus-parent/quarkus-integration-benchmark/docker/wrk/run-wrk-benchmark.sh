#!/bin/bash

# Docker-based wrk benchmark runner for JWT validation
# This script runs wrk in a Docker container to avoid local installation

set -euo pipefail

# Configuration - Optimized for Apple M4 (10 CPU cores)
WRK_IMAGE="cui-jwt-wrk:latest"
QUARKUS_URL="https://cui-jwt-integration-tests:8443"
RESULTS_DIR="./target/benchmark-results"

# Performance settings following wrk best practices  
# Threads: Reduced for stability and to avoid overwhelming the service
THREADS=${1:-4}
# Connections: 20 per thread for optimal distribution (80/4=20)  
CONNECTIONS=${2:-80}
DURATION=${3:-30s}
ERROR_RATE=${4:-0}
REALM=${5:-benchmark}
BENCHMARK_TYPE=${6:-jwt}  # jwt or echo

echo "🚀 Starting Docker-based wrk benchmark..."
echo "  Target: $QUARKUS_URL"
echo "  Threads: $THREADS"
echo "  Connections: $CONNECTIONS"
echo "  Duration: $DURATION"
echo "  Error rate: $ERROR_RATE%"
echo "  Realm: $REALM"
echo "  Benchmark type: $BENCHMARK_TYPE"

# Create results directory
mkdir -p "$RESULTS_DIR"

# Build wrk Docker image if it doesn't exist
if ! docker image inspect "$WRK_IMAGE" >/dev/null 2>&1; then
    echo "📦 Building wrk Docker image..."
    docker build -t "$WRK_IMAGE" ./docker/wrk/
fi

# Multi-realm token support
REALM=${5:-benchmark}
TOKENS_DIR="./target/tokens"
REALM_TOKENS_DIR="$TOKENS_DIR/$REALM"

# Check for realm-specific tokens first, then fall back to default location
if [ -f "$REALM_TOKENS_DIR/access_token.txt" ]; then
    echo "🔑 Loading real JWT tokens from realm: $REALM..."
    ACCESS_TOKEN=$(cat "$REALM_TOKENS_DIR/access_token.txt" | tr -d '\n')
    ID_TOKEN=$(cat "$REALM_TOKENS_DIR/id_token.txt" | tr -d '\n' 2>/dev/null || echo "")
    REFRESH_TOKEN=$(cat "$REALM_TOKENS_DIR/refresh_token.txt" | tr -d '\n' 2>/dev/null || echo "")
    
    TOKEN_ENV_VARS="-e ACCESS_TOKEN=$ACCESS_TOKEN -e ID_TOKEN=$ID_TOKEN -e REFRESH_TOKEN=$REFRESH_TOKEN -e REALM=$REALM"
    echo "  ✅ Real token mode (realm: $REALM): $(echo "$ACCESS_TOKEN" | cut -c1-20)..."
elif [ -f "$TOKENS_DIR/access_token.txt" ]; then
    echo "🔑 Loading real JWT tokens from default location..."
    ACCESS_TOKEN=$(cat "$TOKENS_DIR/access_token.txt" | tr -d '\n')
    ID_TOKEN=$(cat "$TOKENS_DIR/id_token.txt" | tr -d '\n' 2>/dev/null || echo "")
    REFRESH_TOKEN=$(cat "$TOKENS_DIR/refresh_token.txt" | tr -d '\n' 2>/dev/null || echo "")
    
    TOKEN_ENV_VARS="-e ACCESS_TOKEN=$ACCESS_TOKEN -e ID_TOKEN=$ID_TOKEN -e REFRESH_TOKEN=$REFRESH_TOKEN -e REALM=$REALM"
    echo "  ✅ Real token mode (default): $(echo "$ACCESS_TOKEN" | cut -c1-20)..."
else
    echo "🧪 Using mock tokens (no real tokens found)"
    echo "  Searched in: $REALM_TOKENS_DIR/ and $TOKENS_DIR/"
    TOKEN_ENV_VARS=""
fi

# Determine script and endpoint based on benchmark type
if [ "$BENCHMARK_TYPE" = "echo" ]; then
    SCRIPT_NAME="echo-benchmark.lua"
    ENDPOINT="/jwt/echo"
    RESULTS_FILE="echo-benchmark-results.json"
    echo "🔊 Running Echo endpoint benchmark (infrastructure overhead measurement)"
elif [ "$BENCHMARK_TYPE" = "jwt-rotating" ]; then
    SCRIPT_NAME="jwt-benchmark-rotating.lua"
    ENDPOINT="/jwt/validate"
    RESULTS_FILE="jwt-validation-results.json"
    echo "🔄 Running JWT validation benchmark with token rotation (cache testing)"
    
    # Load multiple tokens if available
    ROTATING_TOKEN_ENV_VARS=""
    for i in {1..10}; do
        TOKEN_FILE="$REALM_TOKENS_DIR/multiple/access_token_$i.txt"
        if [ -f "$TOKEN_FILE" ]; then
            TOKEN_VALUE=$(cat "$TOKEN_FILE" | tr -d '\n')
            ROTATING_TOKEN_ENV_VARS="$ROTATING_TOKEN_ENV_VARS -e ACCESS_TOKEN_$i=$TOKEN_VALUE"
            echo "  ✅ Loaded ACCESS_TOKEN_$i"
        fi
    done
    
    # Add rotating tokens to environment
    if [ -n "$ROTATING_TOKEN_ENV_VARS" ]; then
        TOKEN_ENV_VARS="$TOKEN_ENV_VARS $ROTATING_TOKEN_ENV_VARS"
    else
        echo "  ⚠️  No rotating tokens found - using single token (100% cache ratio)"
    fi
else
    SCRIPT_NAME="jwt-benchmark.lua"
    ENDPOINT="/jwt/validate"
    RESULTS_FILE="jwt-validation-results.json"
    echo "🔐 Running JWT validation benchmark"
fi

# Run wrk benchmark in Docker container with conservative resources
echo "🏃 Running wrk benchmark..."
docker run --rm \
    --network cui-jwt-quarkus-integration-tests_jwt-integration \
    --cpus="6" \
    --memory="512m" \
    --ulimit nofile=32768:32768 \
    -v "$PWD/$RESULTS_DIR:/tmp" \
    -e WRK_ERROR_RATE="$ERROR_RATE" \
    $TOKEN_ENV_VARS \
    "$WRK_IMAGE" \
    wrk \
    -t "$THREADS" \
    -c "$CONNECTIONS" \
    -d "$DURATION" \
    --latency \
    --script /benchmark/scripts/$SCRIPT_NAME \
    "$QUARKUS_URL$ENDPOINT" | grep -v "Non-2xx or 3xx responses:"

# Validate performance settings
echo "🔧 Performance Configuration:"
echo "  Docker CPUs: 6 cores allocated"
echo "  Docker Memory: 512MB allocated"
echo "  File descriptors: 32768 (ulimit)"
echo "  Threads: $THREADS (conservative for stability)"
echo "  Connections: $CONNECTIONS ($((CONNECTIONS / THREADS)) per thread)"
echo "  Duration: $DURATION"

# Check if results were generated
if [ -f "$RESULTS_DIR/$RESULTS_FILE" ]; then
    echo "✅ $BENCHMARK_TYPE benchmark completed successfully!"
    echo "📊 Results saved to: $RESULTS_DIR/$RESULTS_FILE"
    
    # Display summary
    echo ""
    echo "=== $BENCHMARK_TYPE Benchmark Performance Summary ==="
    if command -v jq >/dev/null 2>&1 && [ -s "$RESULTS_DIR/$RESULTS_FILE" ]; then
        jq -r '
            "Throughput: " + (.throughput_rps | floor | tostring) + " req/sec",
            "Latency P95: " + (.latency_p95_us | tostring) + "μs", 
            "Latency P99: " + (.latency_p99_us | tostring) + "μs",
            "Errors: " + (.errors | tostring)
        ' "$RESULTS_DIR/$RESULTS_FILE" 2>/dev/null || echo "Results file format issue"
    fi
    
    # Fetch and display JWT metrics from TokenValidatorMonitor (only for JWT benchmarks)
    if [ "$BENCHMARK_TYPE" = "jwt" ]; then
        echo ""
        echo "=== JWT Validation Pipeline Metrics (TokenValidatorMonitor) ==="
        echo "📊 Fetching JWT validation metrics from Prometheus endpoint..."
    
    # Use curl to fetch metrics from inside Docker container (with timeout)
    if docker run --rm \
        --network cui-jwt-quarkus-integration-tests_jwt-integration \
        curlimages/curl:latest \
        -s -k --max-time 10 "https://cui-jwt-integration-tests:8443/q/metrics" | \
    grep -E "cui_jwt_(validation_duration|http_request)" | grep -v "^#" > "$RESULTS_DIR/jwt-metrics-raw.txt" 2>/dev/null; then
        echo "✅ Successfully fetched JWT metrics"
    else
        echo "⚠️  Failed to fetch JWT metrics - continuing without metrics collection"
        touch "$RESULTS_DIR/jwt-metrics-raw.txt" # Create empty file
    fi
    
    if [ -s "$RESULTS_DIR/jwt-metrics-raw.txt" ]; then
        # Parse and display metrics by step
        echo "Processing JWT validation duration metrics..."
        
        # Extract unique steps
        STEPS=$(cat "$RESULTS_DIR/jwt-metrics-raw.txt" | grep -oE 'step="[^"]+' | sed 's/step="//' | sort -u)
        
        for step in $STEPS; do
            echo ""
            echo "Step: $step"
            
            # Get count for this step
            COUNT=$(grep "cui_jwt_validation_duration_seconds_count{step=\"$step\"}" "$RESULTS_DIR/jwt-metrics-raw.txt" | awk '{print $2}' | head -1 || echo "")
            if [ -n "$COUNT" ]; then
                echo "  Count: $COUNT"
            fi
            
            # Get sum for this step and calculate average
            SUM=$(grep "cui_jwt_validation_duration_seconds_sum{step=\"$step\"}" "$RESULTS_DIR/jwt-metrics-raw.txt" | awk '{print $2}' | head -1 || echo "")
            if [ -n "$SUM" ] && [ -n "$COUNT" ] && [ "$COUNT" != "0" ]; then
                AVG=$(awk -v sum="$SUM" -v count="$COUNT" 'BEGIN {printf "%.1f", sum * 1000000 / count}' 2>/dev/null || echo "0.0")
                echo "  Average: ${AVG}μs"
            fi
            
            # Get percentiles
            P50=$(grep "cui_jwt_validation_duration_seconds{step=\"$step\".*quantile=\"0.5\"" "$RESULTS_DIR/jwt-metrics-raw.txt" | awk '{print $2}' | head -1 || echo "")
            if [ -n "$P50" ]; then
                P50_US=$(awk -v p50="$P50" 'BEGIN {printf "%.1f", p50 * 1000000}' 2>/dev/null || echo "0.0")
                echo "  P50: ${P50_US}μs"
            fi
            
            P95=$(grep "cui_jwt_validation_duration_seconds{step=\"$step\".*quantile=\"0.95\"" "$RESULTS_DIR/jwt-metrics-raw.txt" | awk '{print $2}' | head -1 || echo "")
            if [ -n "$P95" ]; then
                P95_US=$(awk -v p95="$P95" 'BEGIN {printf "%.1f", p95 * 1000000}' 2>/dev/null || echo "0.0")
                echo "  P95: ${P95_US}μs"
            fi
            
            P99=$(grep "cui_jwt_validation_duration_seconds{step=\"$step\".*quantile=\"0.99\"" "$RESULTS_DIR/jwt-metrics-raw.txt" | awk '{print $2}' | head -1 || echo "")
            if [ -n "$P99" ]; then
                P99_US=$(awk -v p99="$P99" 'BEGIN {printf "%.1f", p99 * 1000000}' 2>/dev/null || echo "0.0")
                echo "  P99: ${P99_US}μs"
            fi
        done
        
        # Save processed metrics to JSON
        echo ""
        echo "Saving JWT metrics to: $RESULTS_DIR/jwt-validation-metrics.json"
        
        # Create JSON from metrics with benchmark structure similar to JMH output
        echo "{" > "$RESULTS_DIR/jwt-validation-metrics.json"
        echo "  \"quarkusIntegrationBenchmark\": {" >> "$RESULTS_DIR/jwt-validation-metrics.json"
        echo "    \"timestamp\": \"$(date -u +%Y-%m-%dT%H:%M:%S.%6NZ)\"," >> "$RESULTS_DIR/jwt-validation-metrics.json"
        echo "    \"steps\": {" >> "$RESULTS_DIR/jwt-validation-metrics.json"
        
        FIRST_STEP=true
        for step in $STEPS; do
            if [ "$FIRST_STEP" = false ]; then
                echo "," >> "$RESULTS_DIR/jwt-validation-metrics.json"
            fi
            FIRST_STEP=false
            
            # Extract count from timer or distribution summary
            COUNT=$(grep "cui_jwt_validation_duration_seconds_count{step=\"$step\"}" "$RESULTS_DIR/jwt-metrics-raw.txt" | awk '{print $2}' | head -1 || echo "")
            if [ -z "$COUNT" ]; then
                COUNT=$(grep "cui_jwt_validation_duration_percentiles_microseconds_count{step=\"$step\"}" "$RESULTS_DIR/jwt-metrics-raw.txt" | awk '{print $2}' | head -1 || echo "")
            fi
            
            # Extract sum from timer
            SUM=$(grep "cui_jwt_validation_duration_seconds_sum{step=\"$step\"}" "$RESULTS_DIR/jwt-metrics-raw.txt" | awk '{print $2}' | head -1 || echo "")
            
            # Extract percentiles from distribution summary (already in microseconds)
            P50=$(grep "cui_jwt_validation_duration_percentiles_microseconds{step=\"$step\".*quantile=\"0.5\"" "$RESULTS_DIR/jwt-metrics-raw.txt" | awk '{print $2}' | head -1 || echo "")
            P95=$(grep "cui_jwt_validation_duration_percentiles_microseconds{step=\"$step\".*quantile=\"0.95\"" "$RESULTS_DIR/jwt-metrics-raw.txt" | awk '{print $2}' | head -1 || echo "")
            P99=$(grep "cui_jwt_validation_duration_percentiles_microseconds{step=\"$step\".*quantile=\"0.99\"" "$RESULTS_DIR/jwt-metrics-raw.txt" | awk '{print $2}' | head -1 || echo "")
            
            # Fall back to timer quantiles if distribution summary not available (convert seconds to microseconds)
            if [ -z "$P50" ]; then
                P50=$(grep "cui_jwt_validation_duration_seconds{step=\"$step\".*quantile=\"0.5\"" "$RESULTS_DIR/jwt-metrics-raw.txt" | awk '{print $2 * 1000000}' | head -1 || echo "")
            fi
            if [ -z "$P95" ]; then
                P95=$(grep "cui_jwt_validation_duration_seconds{step=\"$step\".*quantile=\"0.95\"" "$RESULTS_DIR/jwt-metrics-raw.txt" | awk '{print $2 * 1000000}' | head -1 || echo "")
            fi
            if [ -z "$P99" ]; then
                P99=$(grep "cui_jwt_validation_duration_seconds{step=\"$step\".*quantile=\"0.99\"" "$RESULTS_DIR/jwt-metrics-raw.txt" | awk '{print $2 * 1000000}' | head -1 || echo "")
            fi
            
            echo -n "      \"$step\": {" >> "$RESULTS_DIR/jwt-validation-metrics.json"
            
            # Start with empty JSON object, add fields as available
            FIRST_FIELD=true
            
            # Add average_us field if we have both sum and count
            if [ -n "$SUM" ] && [ -n "$COUNT" ] && [ "$COUNT" != "0" ] && [ "$COUNT" != "0.0" ]; then
                AVG_JSON=$(awk -v sum="$SUM" -v count="$COUNT" 'BEGIN {printf "%.1f", sum * 1000000 / count}' 2>/dev/null || echo "0.0")
                echo -n "\"average_us\": $AVG_JSON" >> "$RESULTS_DIR/jwt-validation-metrics.json"
                FIRST_FIELD=false
            fi
            
            # Add sample_count if available
            if [ -n "$COUNT" ] && [ "$COUNT" != "0" ] && [ "$COUNT" != "0.0" ]; then
                [ "$FIRST_FIELD" = false ] && echo -n ", " >> "$RESULTS_DIR/jwt-validation-metrics.json"
                echo -n "\"sample_count\": $COUNT" >> "$RESULTS_DIR/jwt-validation-metrics.json"
                FIRST_FIELD=false
            fi
            
            if [ -n "$P50" ]; then
                [ "$FIRST_FIELD" = false ] && echo -n ", " >> "$RESULTS_DIR/jwt-validation-metrics.json"
                # P50 is already in microseconds from distribution summary, no conversion needed
                echo -n "\"p50_us\": $(awk -v p50="$P50" 'BEGIN {printf "%.1f", p50}' 2>/dev/null || echo "0.0")" >> "$RESULTS_DIR/jwt-validation-metrics.json"
                FIRST_FIELD=false
            fi
            
            if [ -n "$P95" ]; then
                [ "$FIRST_FIELD" = false ] && echo -n ", " >> "$RESULTS_DIR/jwt-validation-metrics.json"
                # P95 is already in microseconds from distribution summary, no conversion needed
                echo -n "\"p95_us\": $(awk -v p95="$P95" 'BEGIN {printf "%.1f", p95}' 2>/dev/null || echo "0.0")" >> "$RESULTS_DIR/jwt-validation-metrics.json"
                FIRST_FIELD=false
            fi
            
            if [ -n "$P99" ]; then
                [ "$FIRST_FIELD" = false ] && echo -n ", " >> "$RESULTS_DIR/jwt-validation-metrics.json"
                # P99 is already in microseconds from distribution summary, no conversion needed
                echo -n "\"p99_us\": $(awk -v p99="$P99" 'BEGIN {printf "%.1f", p99}' 2>/dev/null || echo "0.0")" >> "$RESULTS_DIR/jwt-validation-metrics.json"
                FIRST_FIELD=false
            fi
            
            echo -n "}" >> "$RESULTS_DIR/jwt-validation-metrics.json"
        done
        
        echo "" >> "$RESULTS_DIR/jwt-validation-metrics.json"
        echo "    }," >> "$RESULTS_DIR/jwt-validation-metrics.json"
        
        # Add HTTP metrics section
        echo "    \"http_metrics\": {" >> "$RESULTS_DIR/jwt-validation-metrics.json"
        
        # Process HTTP request duration metrics
        HTTP_STEPS=$(cat "$RESULTS_DIR/jwt-metrics-raw.txt" | grep "cui_jwt_http_request_duration_seconds" | grep -oE 'type="[^"]+' | sed 's/type="//' | sort -u)
        
        if [ -n "$HTTP_STEPS" ]; then
            FIRST_HTTP_STEP=true
            for step in $HTTP_STEPS; do
                if [ "$FIRST_HTTP_STEP" = false ]; then
                    echo "," >> "$RESULTS_DIR/jwt-validation-metrics.json"
                fi
                FIRST_HTTP_STEP=false
                
                # Get metrics for this HTTP step
                COUNT=$(grep "cui_jwt_http_request_duration_seconds_count{type=\"$step\"}" "$RESULTS_DIR/jwt-metrics-raw.txt" | awk '{print $2}' | head -1 || echo "")
                SUM=$(grep "cui_jwt_http_request_duration_seconds_sum{type=\"$step\"}" "$RESULTS_DIR/jwt-metrics-raw.txt" | awk '{print $2}' | head -1 || echo "")
                
                echo -n "      \"$step\": " >> "$RESULTS_DIR/jwt-validation-metrics.json"
                
                # Add average_us only if there were actual requests
                if [ -n "$SUM" ] && [ -n "$COUNT" ] && [ "$COUNT" != "0" ] && [ "$COUNT" != "0.0" ]; then
                    AVG_JSON=$(awk -v sum="$SUM" -v count="$COUNT" 'BEGIN {printf "%.1f", sum * 1000000 / count}' 2>/dev/null || echo "0.0")
                    echo -n "{\"average_us\": $AVG_JSON}" >> "$RESULTS_DIR/jwt-validation-metrics.json"
                else
                    # No requests for this type, output empty object
                    echo -n "{}" >> "$RESULTS_DIR/jwt-validation-metrics.json"
                fi
            done
        fi
        
        echo "" >> "$RESULTS_DIR/jwt-validation-metrics.json"
        echo "    }," >> "$RESULTS_DIR/jwt-validation-metrics.json"
        
        # Add HTTP status counts
        echo "    \"http_status_counts\": {" >> "$RESULTS_DIR/jwt-validation-metrics.json"
        
        HTTP_STATUSES=$(cat "$RESULTS_DIR/jwt-metrics-raw.txt" | grep "cui_jwt_http_request_count_requests_total" | grep -oE 'status="[^"]+' | sed 's/status="//' | sort -u)
        
        if [ -n "$HTTP_STATUSES" ]; then
            FIRST_STATUS=true
            for status in $HTTP_STATUSES; do
                if [ "$FIRST_STATUS" = false ]; then
                    echo "," >> "$RESULTS_DIR/jwt-validation-metrics.json"
                fi
                FIRST_STATUS=false
                
                COUNT=$(grep "cui_jwt_http_request_count_requests_total{status=\"$status\"}" "$RESULTS_DIR/jwt-metrics-raw.txt" | awk '{print $2}' | head -1 || echo "")
                if [ -n "$COUNT" ]; then
                    echo -n "      \"$status\": $COUNT" >> "$RESULTS_DIR/jwt-validation-metrics.json"
                else
                    echo -n "      \"$status\": 0" >> "$RESULTS_DIR/jwt-validation-metrics.json"
                fi
            done
        fi
        
        echo "" >> "$RESULTS_DIR/jwt-validation-metrics.json"
        echo "    }" >> "$RESULTS_DIR/jwt-validation-metrics.json"
        echo "  }" >> "$RESULTS_DIR/jwt-validation-metrics.json"
        echo "}" >> "$RESULTS_DIR/jwt-validation-metrics.json"
        
    else
        echo "⚠️  No JWT validation duration metrics found in Prometheus endpoint"
        echo "   This may indicate that TokenValidatorMonitor is not collecting metrics"
    fi
    
    # Process HTTP-level metrics if available
    if grep -q "cui_jwt_http_request" "$RESULTS_DIR/jwt-metrics-raw.txt" 2>/dev/null; then
        echo ""
        echo "=== HTTP-Level JWT Processing Metrics (HttpMetricsMonitor) ==="
        echo "Processing HTTP request metrics..."
        
        # Extract HTTP measurement types
        HTTP_STEPS=$(cat "$RESULTS_DIR/jwt-metrics-raw.txt" | grep "cui_jwt_http_request_duration_seconds" | grep -oE 'type="[^"]+' | sed 's/type="//' | sort -u)
        
        if [ -n "$HTTP_STEPS" ]; then
            for step in $HTTP_STEPS; do
                echo ""
                echo "HTTP Measurement Type: $step"
                
                # Get count and sum for this step
                COUNT=$(grep "cui_jwt_http_request_duration_seconds_count{type=\"$step\"}" "$RESULTS_DIR/jwt-metrics-raw.txt" | awk '{print $2}' | head -1 || echo "")
                SUM=$(grep "cui_jwt_http_request_duration_seconds_sum{type=\"$step\"}" "$RESULTS_DIR/jwt-metrics-raw.txt" | awk '{print $2}' | head -1 || echo "")
                
                # Calculate and display average only
                if [ -n "$SUM" ] && [ -n "$COUNT" ] && [ "$COUNT" != "0" ]; then
                    AVG=$(awk -v sum="$SUM" -v count="$COUNT" 'BEGIN {printf "%.3f", sum * 1000 / count}' 2>/dev/null || echo "0.000")
                    echo "  Average: ${AVG}ms"
                fi
            done
        fi
        
        # Check HTTP request status counts
        echo ""
        echo "=== HTTP Request Status Counts ==="
        HTTP_STATUSES=$(cat "$RESULTS_DIR/jwt-metrics-raw.txt" | grep "cui_jwt_http_request_count_requests_total" | grep -oE 'status="[^"]+' | sed 's/status="//' | sort -u)
        
        if [ -n "$HTTP_STATUSES" ]; then
            for status in $HTTP_STATUSES; do
                COUNT=$(grep "cui_jwt_http_request_count_requests_total{status=\"$status\"}" "$RESULTS_DIR/jwt-metrics-raw.txt" | awk '{print $2}' | head -1 || echo "")
                if [ -n "$COUNT" ]; then
                    echo "  Status $status: $COUNT requests"
                fi
            done
        else
            echo "  No HTTP request status metrics found"
        fi
    else
        echo ""
        echo "⚠️  No HTTP-level JWT metrics found (cui_jwt_http_request)"
        echo "   This may indicate that HttpMetricsMonitor is not collecting metrics"
    fi
    fi  # End of JWT-specific metrics
    
    # Compare with other benchmarks if available
    echo ""
    echo "=== Performance Comparison ==="
    
    # Always show health check baseline if available
    if [ -f "$RESULTS_DIR/health-check-results.json" ]; then
        HEALTH_P95=$(jq -r '.latency_p95_ms' "$RESULTS_DIR/health-check-results.json" 2>/dev/null || echo "0")
        HEALTH_RPS=$(jq -r '.throughput_rps' "$RESULTS_DIR/health-check-results.json" 2>/dev/null || echo "0")
        echo "  Health Check: ${HEALTH_RPS} req/sec @ ${HEALTH_P95}ms P95 (minimal overhead)"
    fi
    
    # Show echo benchmark if available
    if [ -f "$RESULTS_DIR/echo-benchmark-results.json" ]; then
        ECHO_P95=$(jq -r '.latency_p95_ms' "$RESULTS_DIR/echo-benchmark-results.json" 2>/dev/null || echo "0")
        ECHO_RPS=$(jq -r '.throughput_rps' "$RESULTS_DIR/echo-benchmark-results.json" 2>/dev/null || echo "0")
        echo "  Echo Endpoint: ${ECHO_RPS} req/sec @ ${ECHO_P95}ms P95 (CDI + virtual threads)"
    fi
    
    # Show JWT benchmark if available
    if [ -f "$RESULTS_DIR/jwt-validation-results.json" ]; then
        JWT_P95=$(jq -r '.latency_p95_ms' "$RESULTS_DIR/jwt-validation-results.json" 2>/dev/null || echo "0")
        JWT_RPS=$(jq -r '.throughput_rps' "$RESULTS_DIR/jwt-validation-results.json" 2>/dev/null || echo "0")
        echo "  JWT Validation: ${JWT_RPS} req/sec @ ${JWT_P95}ms P95 (full processing)"
    fi
    
    # Calculate overhead if we're running JWT benchmark
    if [ "$BENCHMARK_TYPE" = "jwt" ] && [ -f "$RESULTS_DIR/echo-benchmark-results.json" ]; then
        echo ""
        echo "JWT Processing Overhead Analysis:"
        ECHO_P95=$(jq -r '.latency_p95_ms' "$RESULTS_DIR/echo-benchmark-results.json" 2>/dev/null || echo "0")
        JWT_P95=$(jq -r '.latency_p95_ms' "$RESULTS_DIR/$RESULTS_FILE" 2>/dev/null || echo "0")
        if [ "$ECHO_P95" != "0" ] && [ "$JWT_P95" != "0" ]; then
            JWT_OVERHEAD=$(awk -v jwt="$JWT_P95" -v echo="$ECHO_P95" 'BEGIN {printf "%.1f", jwt - echo}')
            echo "  Pure JWT validation overhead: ${JWT_OVERHEAD}ms (JWT - Echo)"
        fi
    fi
else
    echo "❌ No benchmark results file generated - check Docker logs for errors"
    exit 1
fi

echo "🎉 Docker-based wrk benchmark complete!"