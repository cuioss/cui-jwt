-- JWT Validation Benchmark Script for wrk - REAL TOKENS ONLY
-- Optimized following wrk best practices for high-performance HTTP benchmarking
-- Requires real JWT tokens from Keycloak - NO MOCK TOKENS

-- ============================================================================
-- CONFIGURATION AND CONSTANTS
-- ============================================================================

local path = "/jwt/validate"

-- Pre-generate request pool to minimize runtime overhead (best practice)
-- Pool size optimized for high-throughput testing with minimal memory usage
local request_pool = {}
local request_index = 1
local pool_size = 1000

-- Environment configuration
local error_rate = tonumber(os.getenv("WRK_ERROR_RATE")) or 0

-- REAL TOKENS ONLY - Fail if no real token provided
local access_token = os.getenv("ACCESS_TOKEN")
local id_token = os.getenv("ID_TOKEN")
local refresh_token = os.getenv("REFRESH_TOKEN")

if (not access_token or access_token == "") and 
   (not id_token or id_token == "") and 
   (not refresh_token or refresh_token == "") then
    print("❌ FATAL: No real JWT tokens provided")
    print("   This script requires real JWT tokens from Keycloak")
    print("   Please set ACCESS_TOKEN, ID_TOKEN, or REFRESH_TOKEN environment variables")
    os.exit(1)
end

-- Token pools for different scenarios
local token_pools = {
    valid = {},
    invalid = {},
    expired = {}
}

-- Performance counters
local stats = {
    requests_sent = 0,
    valid_tokens_used = 0,
    invalid_tokens_used = 0,
    pool_regenerations = 0
}

-- ============================================================================
-- TOKEN MANAGEMENT (Optimized for Performance)
-- ============================================================================

local function initialize_token_pools()
    -- Use ONLY real tokens from environment variables - NO MOCK TOKENS
    if access_token and access_token ~= "" then 
        table.insert(token_pools.valid, access_token) 
    end
    if id_token and id_token ~= "" then 
        table.insert(token_pools.valid, id_token) 
    end
    if refresh_token and refresh_token ~= "" then 
        table.insert(token_pools.valid, refresh_token) 
    end
    
    print("✅ Using ONLY real JWT tokens: " .. #token_pools.valid .. " tokens loaded")
    
    -- Only use real tokens for error simulation if error_rate > 0
    if error_rate > 0 then
        print("⚠️  Error rate " .. error_rate .. "% configured - using real tokens for error simulation")
        -- For error simulation, we use intentionally corrupted real tokens
        for i, token in ipairs(token_pools.valid) do
            -- Create invalid tokens by corrupting the signature
            table.insert(token_pools.invalid, token .. ".corrupted")
            -- Create expired tokens by corrupting the payload  
            table.insert(token_pools.expired, token:gsub("%.([^%.]+)%.", ".expired."))
        end
    end
    
    if #token_pools.valid == 0 then
        print("❌ No valid real tokens available - benchmark will fail")
        os.exit(1)
    end
end

local function select_token_by_error_rate()
    local random_value = math.random(1, 100)
    
    if random_value <= error_rate then
        -- Return error token (invalid or expired)
        if random_value <= error_rate / 2 and #token_pools.invalid > 0 then
            stats.invalid_tokens_used = stats.invalid_tokens_used + 1
            return token_pools.invalid[math.random(1, #token_pools.invalid)]
        elseif #token_pools.expired > 0 then
            stats.invalid_tokens_used = stats.invalid_tokens_used + 1
            return token_pools.expired[math.random(1, #token_pools.expired)]
        end
    end
    
    -- Return valid token
    stats.valid_tokens_used = stats.valid_tokens_used + 1
    return token_pools.valid[math.random(1, #token_pools.valid)]
end

-- ============================================================================
-- REQUEST PRE-GENERATION (Performance Optimization)
-- ============================================================================

local function generate_request_pool()
    print("🔄 Pre-generating " .. pool_size .. " HTTP requests...")
    
    for i = 1, pool_size do
        local token = select_token_by_error_rate()
        local body = '{"token":"' .. token .. '"}'
        local headers = {
            ["Content-Type"] = "application/json",
            ["Authorization"] = "Bearer " .. token
        }
        
        request_pool[i] = wrk.format("POST", path, headers, body)
    end
    
    stats.pool_regenerations = stats.pool_regenerations + 1
    print("✅ Request pool generated (generation #" .. stats.pool_regenerations .. ")")
end

-- ============================================================================
-- WRK LIFECYCLE FUNCTIONS
-- ============================================================================

function setup(thread)
    -- Thread-specific initialization with optimized random seeding
    -- Use high-resolution timer for better distribution
    math.randomseed(os.time() * 1000 + (thread and 1 or 0))
    print("🧵 Thread initialized")
end

function init(args)
    -- Initialize token pools
    initialize_token_pools()
    
    -- Pre-generate request pool for performance
    generate_request_pool()
    
    print("🚀 JWT benchmark initialized:")
    print("   Error rate: " .. error_rate .. "%")
    print("   Pool size: " .. pool_size .. " requests (optimized for throughput)")
    print("   Token mode: REAL TOKENS ONLY")
    print("   Performance: Pre-generated request pool for minimal runtime overhead")
end

function request()
    stats.requests_sent = stats.requests_sent + 1
    
    -- Use pre-generated request from pool (best practice for performance)
    local request = request_pool[request_index]
    request_index = request_index + 1
    
    -- Regenerate pool when exhausted
    if request_index > pool_size then
        generate_request_pool()
        request_index = 1
    end
    
    return request
end

function response(status, headers, body)
    -- Minimal response processing for maximum performance
    if status ~= 200 and status ~= 400 and status ~= 401 then
        io.write("⚠️  Unexpected status: " .. status .. "\n")
    end
end

function done(summary, latency, requests)
    print("\n🏆 === JWT Validation Benchmark Results ===")
    print("📊 Total Requests: " .. summary.requests)
    print("⏱️  Duration: " .. string.format("%.2f", summary.duration / 1000000) .. " seconds")
    
    local throughput = summary.requests / (summary.duration / 1000000)
    print("⚡ Throughput: " .. string.format("%.2f", throughput) .. " req/sec")
    
    print("🚥 Errors: " .. (summary.errors.connect + summary.errors.read + summary.errors.write + summary.errors.timeout))
    
    print("\n📈 Latency Distribution:")
    print("  50th percentile: " .. string.format("%.2f", latency:percentile(50)) .. "ms")
    print("  90th percentile: " .. string.format("%.2f", latency:percentile(90)) .. "ms")
    print("  95th percentile: " .. string.format("%.2f", latency:percentile(95)) .. "ms")
    print("  99th percentile: " .. string.format("%.2f", latency:percentile(99)) .. "ms")
    
    print("\n🔧 Performance Stats:")
    print("  Valid tokens used: " .. stats.valid_tokens_used)
    print("  Invalid tokens used: " .. stats.invalid_tokens_used)
    print("  Pool regenerations: " .. stats.pool_regenerations)
    
    -- Generate JSON results for processing
    local results = string.format([[{
        "timestamp": "%s",
        "benchmark_type": "jwt_validation",
        "token_mode": "%s",
        "error_rate": %d,
        "requests": %d,
        "duration_ms": %d,
        "throughput_rps": %.2f,
        "latency": {
            "p50": %.2f,
            "p90": %.2f,
            "p95": %.2f,
            "p99": %.2f,
            "max": %.2f
        },
        "errors": {
            "connect": %d,
            "read": %d,
            "write": %d,
            "timeout": %d,
            "total": %d
        },
        "performance_stats": {
            "valid_tokens_used": %d,
            "invalid_tokens_used": %d,
            "pool_regenerations": %d,
            "success_rate": %.2f
        }
    }]], 
    os.date("%Y-%m-%dT%H:%M:%SZ"),
    "real",
    error_rate,
    summary.requests, 
    summary.duration / 1000,
    throughput,
    latency:percentile(50),
    latency:percentile(90),
    latency:percentile(95),
    latency:percentile(99),
    latency.max,
    summary.errors.connect,
    summary.errors.read,
    summary.errors.write,
    summary.errors.timeout,
    summary.errors.connect + summary.errors.read + summary.errors.write + summary.errors.timeout,
    stats.valid_tokens_used,
    stats.invalid_tokens_used,
    stats.pool_regenerations,
    ((summary.requests - (summary.errors.connect + summary.errors.read + summary.errors.write + summary.errors.timeout)) / summary.requests) * 100)
    
    local file = io.open("/tmp/wrk-results.json", "w")
    if file then
        file:write(results)
        file:close()
        print("\n💾 Results written to /tmp/wrk-results.json")
    else
        print("\n❌ Failed to write results file")
    end
    
    print("\n🎯 Summary: " .. string.format("%.0f", throughput) .. " req/sec @ " .. string.format("%.0f", latency:percentile(95)) .. "ms P95")
end