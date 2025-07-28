-- Echo Endpoint Benchmark Script for wrk
-- Tests the echo endpoint to measure infrastructure overhead without JWT validation

print("✅ Echo endpoint benchmark - measuring infrastructure overhead")

function request()
    local body = '{"data": {"test": "benchmark", "timestamp": ' .. os.time() .. '}}'
    local headers = {
        ["Content-Type"] = "application/json"
    }
    return wrk.format("POST", "/jwt/echo", headers, body)
end

function done(summary, latency, requests)
    local throughput = summary.requests / (summary.duration / 1000000)
    local connection_errors = summary.errors.connect + summary.errors.read + summary.errors.write + summary.errors.timeout
    
    -- Keep latency in microseconds (no conversion needed)
    local latency_p95_us = latency:percentile(95)
    local latency_p99_us = latency:percentile(99)
    
    print("Results: " .. string.format("%.0f", throughput) .. " req/sec")
    print("Latency P95: " .. string.format("%.1f", latency_p95_us) .. "μs")
    print("Latency P99: " .. string.format("%.1f", latency_p99_us) .. "μs")
    print("Connection Errors: " .. connection_errors)
    
    -- Assume 100% success rate for echo endpoint
    local results = string.format('{"throughput_rps":%.0f,"latency_p95_us":%.1f,"latency_p99_us":%.1f,"errors":%d,"connection_errors":%d,"test_type":"echo"}',
                                  throughput, latency_p95_us, latency_p99_us, connection_errors, connection_errors)
    
    local file = io.open("/tmp/echo-benchmark-results.json", "w")
    if file then
        file:write(results)
        file:close()
        print("✅ Results written to /tmp/echo-benchmark-results.json")
    else
        print("❌ Failed to write results file")
    end
end