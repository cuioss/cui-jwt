-- Minimal JWT Validation Script for wrk

local access_token = os.getenv("ACCESS_TOKEN") or "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWV9.mock-token"

function request()
    local body = '{"token":"' .. access_token .. '"}'
    local headers = "Content-Type: application/json\r\n"
    return wrk.format("POST", "/jwt/validate", headers, body)
end

function done(summary, latency, requests)
    local throughput = summary.requests / (summary.duration / 1000000)
    local total_errors = summary.errors.connect + summary.errors.read + summary.errors.write + summary.errors.timeout
    
    print("🏆 Results: " .. string.format("%.0f", throughput) .. " req/sec")
    print("📈 Latency P95: " .. string.format("%.0f", latency:percentile(95)) .. "ms")
    print("🚥 Errors: " .. total_errors)
    
    local results = string.format('{"throughput_rps":%.0f,"latency_p95_ms":%.0f,"errors":%d}', 
        throughput, latency:percentile(95), total_errors)
    
    local file = io.open("/tmp/wrk-results.json", "w")
    if file then
        file:write(results)
        file:close()
    end
end