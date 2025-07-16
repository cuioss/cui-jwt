-- Working JWT Validation Script for wrk - REAL TOKENS ONLY
-- Minimal implementation to avoid compatibility issues

local access_token = os.getenv("ACCESS_TOKEN")

-- Fail immediately if no real token is provided
if not access_token or access_token == "" then
    print("❌ FATAL: No real ACCESS_TOKEN provided")
    os.exit(1)
end

print("✅ Using real JWT token")

function request()
    local body = '{"token":"' .. access_token .. '"}'
    local headers = {
        ["Content-Type"] = "application/json"
    }
    return wrk.format("POST", "/jwt/validate", headers, body)
end

function done(summary, latency, requests)
    local throughput = summary.requests / (summary.duration / 1000000)
    local total_errors = summary.errors.connect + summary.errors.read + summary.errors.write + summary.errors.timeout
    
    print("Results: " .. string.format("%.0f", throughput) .. " req/sec")
    print("Latency P95: " .. string.format("%.0f", latency:percentile(95)) .. "ms")
    print("Errors: " .. total_errors)
    
    -- Write simple results file
    local results = '{"throughput_rps":' .. string.format("%.0f", throughput) .. 
                   ',"latency_p95_ms":' .. string.format("%.0f", latency:percentile(95)) .. 
                   ',"errors":' .. total_errors .. '}'
    
    local file = io.open("/tmp/wrk-results.json", "w")
    if file then
        file:write(results)
        file:close()
    end
end