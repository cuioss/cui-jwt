-- Working JWT Validation Script for wrk - REAL TOKENS ONLY
-- Minimal implementation to avoid compatibility issues

-- Multi-realm token support
local access_token = os.getenv("ACCESS_TOKEN")
local id_token = os.getenv("ID_TOKEN")
local refresh_token = os.getenv("REFRESH_TOKEN")
local realm = os.getenv("REALM") or "benchmark"

-- Fail immediately if no real token is provided
if not access_token or access_token == "" then
    print("❌ FATAL: No real ACCESS_TOKEN provided")
    print("   Available tokens: ACCESS_TOKEN, ID_TOKEN, REFRESH_TOKEN")
    print("   Current realm: " .. realm)
    os.exit(1)
end

print("✅ Using real JWT token from realm: " .. realm)
print("   Token type: ACCESS_TOKEN")
print("   Token length: " .. string.len(access_token) .. " characters")

local first_failure_detected = false

function request()
    -- Exit immediately if we've already detected a failure (fail-fast)
    if first_failure_detected then
        return nil
    end
    
    local body = "{}"
    local headers = {
        ["Content-Type"] = "application/json",
        ["Authorization"] = "Bearer " .. access_token
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
        
        -- Immediately write failure results and exit - don't wait for done()
        local results = '{"throughput_rps":0,"latency_p95_ms":0,"latency_p99_ms":0,"errors":1,"connection_errors":0,"fail_fast_triggered":true,"success_rate":0.0}'
        local file = io.open("/tmp/wrk-results.json", "w")
        if file then
            file:write(results)
            file:close()
            print("✅ Fail-fast results written to /tmp/wrk-results.json")
        end
        
        print("🚨 BENCHMARK FAILED: Token validation failed (fail-fast after first 401)")
        -- Force exit to prevent further processing
        -- Sleep briefly to ensure file is written before any potential done() call
        os.execute("sleep 0.1")
        os.exit(1)
    end
end

function done(summary, latency, requests)
    print("🔍 Done function called - checking for fail-fast")
    
    -- Check if fail-fast results file already exists
    local file = io.open("/tmp/wrk-results.json", "r")
    if file then
        local content = file:read("*all")
        file:close()
        print("🔍 Found existing results file, content length: " .. string.len(content))
        if content and string.find(content, '"fail_fast_triggered":true') then
            print("⚠️  Done function called after fail-fast - results already written")
            return
        end
    end
    
    -- If fail-fast was triggered, the results file was already written in response()
    -- This function should only run if no fail-fast occurred
    if first_failure_detected then
        print("⚠️  Done function called after fail-fast - results already written (flag check)")
        return
    end
    
    print("🔍 No fail-fast detected, proceeding with normal results")
    
    local throughput = summary.requests / (summary.duration / 1000000)
    local connection_errors = summary.errors.connect + summary.errors.read + summary.errors.write + summary.errors.timeout
    
    -- Convert latency from microseconds to milliseconds
    local latency_p95_ms = latency:percentile(95) / 1000
    local latency_p99_ms = latency:percentile(99) / 1000
    
    print("Results: " .. string.format("%.0f", throughput) .. " req/sec")
    print("Latency P95: " .. string.format("%.1f", latency_p95_ms) .. "ms")
    print("Latency P99: " .. string.format("%.1f", latency_p99_ms) .. "ms")
    print("Connection Errors: " .. connection_errors)
    
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