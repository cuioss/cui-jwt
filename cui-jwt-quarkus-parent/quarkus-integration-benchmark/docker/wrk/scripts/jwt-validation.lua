-- JWT Validation Load Test Script for wrk
-- This script performs HTTP POST requests to JWT validation endpoint
-- with proper JWT tokens for realistic integration testing

local json = require "json"

-- Configuration
local jwt_tokens = {}
local token_index = 1
local request_count = 0

-- JWT validation endpoint
local path = "/jwt/validate"

-- Initialize tokens (will be populated by setup function)
function setup(thread)
    -- Sample JWT tokens for testing
    -- In real implementation, these would be fetched from Keycloak
    jwt_tokens = {
        "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWV9.sample-token-1",
        "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWV9.sample-token-2",
        "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWV9.sample-token-3"
    }
    
    print("Initialized with " .. #jwt_tokens .. " test tokens")
end

-- Generate request with rotating JWT tokens
function request()
    request_count = request_count + 1
    
    -- Rotate through available tokens
    local token = jwt_tokens[token_index]
    token_index = token_index + 1
    if token_index > #jwt_tokens then
        token_index = 1
    end
    
    -- Create request body with token
    local body = json.encode({
        token = token
    })
    
    -- Set headers
    local headers = {
        ["Content-Type"] = "application/json",
        ["Authorization"] = "Bearer " .. token
    }
    
    -- Build header string
    local header_str = ""
    for key, value in pairs(headers) do
        header_str = header_str .. key .. ": " .. value .. "\r\n"
    end
    
    -- Return HTTP request
    return wrk.format("POST", path, header_str, body)
end

-- Handle response
function response(status, headers, body)
    if status ~= 200 then
        print("Error response: " .. status .. " - " .. body)
    end
end

-- Print final statistics
function done(summary, latency, requests)
    print("\n=== JWT Validation Benchmark Results ===")
    print("Requests: " .. summary.requests)
    print("Duration: " .. summary.duration .. "ms")
    print("Errors: " .. summary.errors.connect .. " connect, " .. summary.errors.read .. " read, " .. summary.errors.write .. " write, " .. summary.errors.timeout .. " timeout")
    print("Throughput: " .. (summary.requests / (summary.duration / 1000000)) .. " req/sec")
    
    -- Latency percentiles
    print("\nLatency Distribution:")
    print("  50th percentile: " .. latency:percentile(50) .. "ms")
    print("  90th percentile: " .. latency:percentile(90) .. "ms")
    print("  95th percentile: " .. latency:percentile(95) .. "ms")
    print("  99th percentile: " .. latency:percentile(99) .. "ms")
    
    -- Create JSON output for processing
    local results = {
        requests = summary.requests,
        duration_ms = summary.duration,
        errors = summary.errors,
        throughput_rps = summary.requests / (summary.duration / 1000000),
        latency = {
            p50 = latency:percentile(50),
            p90 = latency:percentile(90),
            p95 = latency:percentile(95),
            p99 = latency:percentile(99)
        }
    }
    
    -- Write results to file for badge generation
    local file = io.open("/tmp/wrk-results.json", "w")
    if file then
        file:write(json.encode(results))
        file:close()
        print("\nResults written to /tmp/wrk-results.json")
    end
end