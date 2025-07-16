-- Real JWT Validation Test for wrk
-- Uses actual JWT tokens fetched from Keycloak for realistic testing

local path = "/jwt/validate"
local request_count = 0

-- Real JWT tokens (will be loaded from environment variables)
local access_token = os.getenv("ACCESS_TOKEN") or ""
local id_token = os.getenv("ID_TOKEN") or ""
local refresh_token = os.getenv("REFRESH_TOKEN") or ""

-- Token pool for rotation
local tokens = {}
local token_index = 1

function setup(thread)
    -- Build token pool from available tokens
    if access_token ~= "" then
        table.insert(tokens, access_token)
    end
    if id_token ~= "" then
        table.insert(tokens, id_token)
    end
    if refresh_token ~= "" then
        table.insert(tokens, refresh_token)
    end
    
    if #tokens == 0 then
        print("❌ No valid tokens available in environment variables")
        print("   Please set ACCESS_TOKEN, ID_TOKEN, or REFRESH_TOKEN")
        os.exit(1)
    end
    
    print("✅ Thread initialized with " .. #tokens .. " real JWT tokens")
    print("   Token lengths: " .. string.len(tokens[1]) .. " chars (first token)")
end

function request()
    request_count = request_count + 1
    
    -- Ensure we have tokens
    if #tokens == 0 then
        print("❌ No tokens available")
        return wrk.format("GET", "/error", "", "")
    end
    
    -- Get current token (using modulo for safety)
    local current_index = ((request_count - 1) % #tokens) + 1
    local token = tokens[current_index]
    
    if not token or token == "" then
        print("❌ Token at index " .. current_index .. " is nil or empty")
        return wrk.format("GET", "/error", "", "")
    end
    
    -- Create JSON body
    local body = '{"token":"' .. token .. '"}'
    
    -- Create headers
    local headers = "Content-Type: application/json\r\n"
    headers = headers .. "Authorization: Bearer " .. token .. "\r\n"
    
    return wrk.format("POST", path, headers, body)
end

function response(status, headers, body)
    if status ~= 200 then
        print("❌ Error response: " .. status .. " - " .. (body or ""))
    end
end

function done(summary, latency, requests)
    print("\n🏆 === Real JWT Validation Results ===")
    print("📊 Requests: " .. summary.requests)
    print("⏱️  Duration: " .. summary.duration .. "ms")
    print("⚡ Throughput: " .. string.format("%.2f", summary.requests / (summary.duration / 1000000)) .. " req/sec")
    print("🚥 Errors: " .. summary.errors.connect .. " connect, " .. summary.errors.read .. " read, " .. summary.errors.write .. " write, " .. summary.errors.timeout .. " timeout")
    
    print("\n📈 Latency Distribution:")
    print("  50th percentile: " .. string.format("%.2f", latency:percentile(50)) .. "ms")
    print("  90th percentile: " .. string.format("%.2f", latency:percentile(90)) .. "ms")
    print("  95th percentile: " .. string.format("%.2f", latency:percentile(95)) .. "ms")
    print("  99th percentile: " .. string.format("%.2f", latency:percentile(99)) .. "ms")
    
    -- Create results for processing
    local throughput_rps = summary.requests / (summary.duration / 1000000)
    local results = string.format([[{
        "requests": %d,
        "duration_ms": %d,
        "throughput_rps": %.2f,
        "latency": {
            "p50": %.2f,
            "p90": %.2f,
            "p95": %.2f,
            "p99": %.2f
        },
        "errors": {
            "connect": %d,
            "read": %d,
            "write": %d,
            "timeout": %d
        },
        "tokens_used": %d
    }]], 
    summary.requests, 
    summary.duration, 
    throughput_rps,
    latency:percentile(50),
    latency:percentile(90),
    latency:percentile(95),
    latency:percentile(99),
    summary.errors.connect,
    summary.errors.read,
    summary.errors.write,
    summary.errors.timeout,
    #tokens)
    
    local file = io.open("/tmp/wrk-results.json", "w")
    if file then
        file:write(results)
        file:close()
        print("\n💾 Results written to /tmp/wrk-results.json")
    end
    
    print("\n🎯 === Performance Summary ===")
    print("   Throughput: " .. string.format("%.0f", throughput_rps) .. " req/sec")
    print("   Latency P95: " .. string.format("%.0f", latency:percentile(95)) .. "ms")
    print("   Success Rate: " .. string.format("%.1f", ((summary.requests - summary.errors.connect - summary.errors.read - summary.errors.write - summary.errors.timeout) / summary.requests) * 100) .. "%")
end