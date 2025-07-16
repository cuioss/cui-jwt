-- Health Check Benchmark Script for wrk - Pure System Latency
-- Measures container/network overhead without JWT validation

print("✅ Health check benchmark - measuring pure system latency")

function request()
    return wrk.format("GET", "/q/health/live")
end

function done(summary, latency, requests)
    local throughput = summary.requests / (summary.duration / 1000000)
    local total_errors = summary.errors.connect + summary.errors.read + summary.errors.write + summary.errors.timeout
    
    -- Convert latency from microseconds to milliseconds
    local latency_p95_ms = latency:percentile(95) / 1000
    local latency_p99_ms = latency:percentile(99) / 1000
    
    print("Results: " .. string.format("%.0f", throughput) .. " req/sec")
    print("Latency P95: " .. string.format("%.1f", latency_p95_ms) .. "ms")
    print("Latency P99: " .. string.format("%.1f", latency_p99_ms) .. "ms")
    print("Errors: " .. total_errors)
    
    -- Write results file for health check baseline
    local results = string.format('{"throughput_rps":%.0f,"latency_p95_ms":%.1f,"latency_p99_ms":%.1f,"errors":%d,"test_type":"health_check"}',
                                  throughput, latency_p95_ms, latency_p99_ms, total_errors)
    
    local file = io.open("/tmp/health-check-results.json", "w")
    if file then
        file:write(results)
        file:close()
        print("✅ Health check results written to /tmp/health-check-results.json")
    else
        print("❌ Failed to write health check results file")
    end
end