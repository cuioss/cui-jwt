# WRK Script Analysis and Fixes

## Issues Identified

### 1. Path Override Problem
Both `health_check.lua` and `jwt_benchmark.lua` were overriding the URL path, conflicting with the full URLs provided by shell scripts:

**health_check.lua:**
- Script set: `wrk.path = "/q/health"`
- Shell provided: `"$SERVICE_URL/q/health/live"`
- **Result**: WRK was testing wrong endpoint, stats counters showed 0

**jwt_benchmark.lua:**
- Script set: `wrk.path = "/jwt/validate"`
- Shell provided: `"$SERVICE_URL/jwt/validate"`
- **Result**: Potential path duplication/conflict

### 2. Missing Statistics Output
`jwt_benchmark.lua` had its `done()` function commented out (lines 109-111), preventing custom statistics output.

### 3. Current Performance Numbers
Raw WRK output shows:
- Health Check: **5,681.72 req/s** (not 20k+ as expected)
- JWT Validation: **9,353.29 req/s**

These numbers are accurately reflected in computed metrics. The issue is actual performance, not computation.

## Fixes Applied

### 1. Removed Path Overrides
Both scripts now respect the full URL passed from shell scripts:
```lua
-- OLD
wrk.path = "/q/health"  -- or "/jwt/validate"

-- NEW
-- Path is already set by WRK from the command line URL
-- Don't override it here to allow flexibility in shell script
```

### 2. Added Statistics Output to JWT Script
Added proper `done()` function to `jwt_benchmark.lua` for consistent reporting:
```lua
function done(summary, latency, requests)
    print("=== JWT Validation Benchmark Results ===")
    print(string.format("Duration:          %.2fs", summary.duration / 1000000))
    print(string.format("Total requests:    %d", summary.requests))
    print(string.format("Requests/sec:      %.2f", summary.requests / (summary.duration / 1000000)))
    -- ... percentiles ...
end
```

## Expected Impact

1. **Health check stats** should now properly track requests/success/errors
2. **Consistent output** between health and JWT benchmarks
3. **Flexibility** to test different endpoints without modifying Lua scripts
4. **Better debugging** with detailed statistics in both benchmarks

## Performance Expectations

The current performance (5.6k req/s for health, 9.3k req/s for JWT) appears to be the actual throughput. The expected 20k+ for health checks may have been:
- From different test conditions
- Using a simpler endpoint (`/q/health` vs `/q/health/live`)
- From JMH benchmarks which show 11.6k ops/s for health checks

## Performance Comparison Results

### Bug Discovery: Both scripts testing same endpoint
**Issue**: `health_benchmark.sh` was incorrectly configured to test `/q/health/live` instead of `/q/health`, making both benchmarks test the same endpoint.

### Current Results (Both testing /q/health/live):
- **health_benchmark.sh**: 3,450.12 req/s (but testing wrong endpoint)
- **health_live_benchmark.sh**: 5,114.45 req/s
- **Performance difference**: ~48% faster in second run

### Key Finding: Health Check Failures
Both benchmarks show **100% failure rate** with HTTP 503 responses:
- Status: "DOWN"
- Failed check: "jwt-validator"
- Quarkus logs show: "No configuration found for issuer: https://keycloak:8443/realms/benchmark"

This indicates a **configuration bug** where the JWT validator health check is failing, causing all health endpoints to return unhealthy status.

## Next Steps

1. Fix `health_benchmark.sh` to actually test `/q/health` endpoint
2. Fix JWT validator configuration to resolve health check failures
3. Re-run benchmarks with proper configuration
4. Compare true performance between `/q/health` vs `/q/health/live`