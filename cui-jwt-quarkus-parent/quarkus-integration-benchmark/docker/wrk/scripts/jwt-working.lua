-- Working JWT Validation Script for wrk - REAL TOKENS ONLY
-- Minimal implementation to avoid compatibility issues

local access_token = os.getenv("ACCESS_TOKEN")

-- Fail immediately if no real token is provided
if not access_token or access_token == "" then
    print("❌ FATAL: No real ACCESS_TOKEN provided")
    os.exit(1)
end

print("✅ Using real JWT token")

local first_failure_detected = false

function request()
    -- Exit immediately if we've already detected a failure (fail-fast)
    if first_failure_detected then
        return nil
    end
    
    local body = '{"token":"' .. access_token .. '"}'
    local headers = {
        ["Content-Type"] = "application/json"
    }
    return wrk.format("POST", "/jwt/validate", headers, body)
end

function response(status, headers, body)
    -- Check for unexpected 401 responses with valid tokens - fail fast
    if status == 401 then
        -- Parse the response to extract just the message, removing the data:{} field
        local response_message = body or "no body"
        local message_match = string.match(response_message, '"message":"([^"]*)"')
        local clean_message = message_match and message_match or response_message
        
        print("⚠️  Unexpected 401 with valid token: " .. clean_message .. ", token: " .. access_token)
        print("🚨 FAIL-FAST: First token validation failure detected - stopping benchmark")
        first_failure_detected = true
    end
end

function done(summary, latency, requests)
    local throughput = summary.requests / (summary.duration / 1000000)
    local connection_errors = summary.errors.connect + summary.errors.read + summary.errors.write + summary.errors.timeout
    
    -- Convert latency from microseconds to milliseconds
    local latency_p95_ms = latency:percentile(95) / 1000
    local latency_p99_ms = latency:percentile(99) / 1000
    
    print("Results: " .. string.format("%.0f", throughput) .. " req/sec")
    print("Latency P95: " .. string.format("%.1f", latency_p95_ms) .. "ms")
    print("Latency P99: " .. string.format("%.1f", latency_p99_ms) .. "ms")
    print("Connection Errors: " .. connection_errors)
    
    -- Check if we had a failure (fail-fast mode)
    if first_failure_detected then
        print("\n❌ VALIDATION ERROR: Token validation failed (fail-fast after first 401)")
        print("   This indicates token validation issues or configuration problems")
        
        -- Write results file with failure information
        local results = string.format('{"throughput_rps":%.0f,"latency_p95_ms":%.1f,"latency_p99_ms":%.1f,"errors":%d,"connection_errors":%d,"fail_fast_triggered":true,"success_rate":0.0}',
                                      throughput, latency_p95_ms, latency_p99_ms, connection_errors + 1, connection_errors)
        
        local file = io.open("/tmp/wrk-results.json", "w")
        if file then
            file:write(results)
            file:close()
            print("✅ Results written to /tmp/wrk-results.json")
        else
            print("❌ Failed to write results file")
        end
        
        print("\n🚨 BENCHMARK FAILED: Token validation failed (fail-fast after first 401)")
        os.exit(1)
    else
        -- No failures detected - successful benchmark
        local results = string.format('{"throughput_rps":%.0f,"latency_p95_ms":%.1f,"latency_p99_ms":%.1f,"errors":%d,"connection_errors":%d,"fail_fast_triggered":false,"success_rate":100.0}',
                                      throughput, latency_p95_ms, latency_p99_ms, connection_errors, connection_errors)
        
        local file = io.open("/tmp/wrk-results.json", "w")
        if file then
            file:write(results)
            file:close()
            print("✅ Results written to /tmp/wrk-results.json")
        else
            print("❌ Failed to write results file")
        end
    end
end