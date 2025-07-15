-- Simple JWT Validation Test for wrk
-- Basic POST request without complex TokenRepository for initial testing

local path = "/jwt/validate"
local request_count = 0

-- Sample JWT token
local jwt_token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWV9.valid-token-test"

function setup(thread)
    print("Thread initialized")
end

function request()
    request_count = request_count + 1
    
    -- Create JSON body
    local body = '{"token":"' .. jwt_token .. '"}'
    
    -- Create headers
    local headers = "Content-Type: application/json\r\n"
    headers = headers .. "Authorization: Bearer " .. jwt_token .. "\r\n"
    
    return wrk.format("POST", path, headers, body)
end

function response(status, headers, body)
    if status ~= 200 then
        print("Error response: " .. status)
    end
end

function done(summary, latency, requests)
    print("\n=== Simple JWT Validation Results ===")
    print("Requests: " .. summary.requests)
    print("Duration: " .. summary.duration .. "ms")
    print("Throughput: " .. (summary.requests / (summary.duration / 1000000)) .. " req/sec")
    print("Latency 95th: " .. latency:percentile(95) .. "ms")
    
    -- Simple results output
    local results = string.format([[{
        "requests": %d,
        "duration_ms": %d,
        "throughput_rps": %.2f,
        "latency": {
            "p95": %.2f
        }
    }]], summary.requests, summary.duration, summary.requests / (summary.duration / 1000000), latency:percentile(95))
    
    local file = io.open("/tmp/wrk-results.json", "w")
    if file then
        file:write(results)
        file:close()
        print("Results written to /tmp/wrk-results.json")
    end
end