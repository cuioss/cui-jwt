-- Simple JWT Validation Benchmark Script for wrk
-- Optimized for reliability and performance

local path = "/jwt/validate"
local request_count = 0

-- Load real tokens if available
local access_token = os.getenv("ACCESS_TOKEN") or ""
local use_real_tokens = access_token ~= ""

-- Mock token for testing
local mock_token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWV9.mock-token"

function init(args)
    if use_real_tokens then
        print("✅ Using real JWT token: " .. string.sub(access_token, 1, 20) .. "...")
    else
        print("🧪 Using mock JWT token for testing")
    end
end

function request()
    request_count = request_count + 1
    
    local token = use_real_tokens and access_token or mock_token
    local body = '{"token":"' .. token .. '"}'
    local headers = "Content-Type: application/json\r\nAuthorization: Bearer " .. token .. "\r\n"
    
    return wrk.format("POST", path, headers, body)
end

function done(summary, latency, requests)
    print("\n🏆 === JWT Validation Results ===")
    print("📊 Requests: " .. summary.requests)
    print("⏱️  Duration: " .. string.format("%.2f", summary.duration / 1000000) .. " seconds")
    
    local throughput = summary.requests / (summary.duration / 1000000)
    print("⚡ Throughput: " .. string.format("%.0f", throughput) .. " req/sec")
    
    local total_errors = summary.errors.connect + summary.errors.read + summary.errors.write + summary.errors.timeout
    print("🚥 Errors: " .. total_errors)
    
    print("\n📈 Latency Distribution:")
    print("  50th percentile: " .. string.format("%.0f", latency:percentile(50)) .. "ms")
    print("  90th percentile: " .. string.format("%.0f", latency:percentile(90)) .. "ms")
    print("  95th percentile: " .. string.format("%.0f", latency:percentile(95)) .. "ms")
    print("  99th percentile: " .. string.format("%.0f", latency:percentile(99)) .. "ms")
    
    -- Generate simple JSON results
    local results = string.format([[{
    "requests": %d,
    "duration_ms": %d,
    "throughput_rps": %.0f,
    "latency_p95_ms": %.0f,
    "latency_p99_ms": %.0f,
    "errors": %d,
    "success_rate": %.1f
}]], 
    summary.requests,
    summary.duration / 1000,
    throughput,
    latency:percentile(95),
    latency:percentile(99),
    total_errors,
    ((summary.requests - total_errors) / summary.requests) * 100)
    
    local file = io.open("/tmp/wrk-results.json", "w")
    if file then
        file:write(results)
        file:close()
        print("\n💾 Results written to /tmp/wrk-results.json")
    end
    
    print("\n🎯 Summary: " .. string.format("%.0f", throughput) .. " req/sec @ " .. string.format("%.0f", latency:percentile(95)) .. "ms P95")
end