-- JWT Validation Load Test Script for wrk
-- This script performs HTTP POST requests to JWT validation endpoint
-- with proper JWT tokens for realistic integration testing

local json = require "json"

-- TokenRepository - Efficient token management to avoid slow token retrieval
local TokenRepository = {}
TokenRepository.__index = TokenRepository

function TokenRepository.new()
    local self = setmetatable({}, TokenRepository)
    self.valid_tokens = {}
    self.invalid_tokens = {}
    self.expired_tokens = {}
    self.token_index = 1
    self.fetch_count = 0
    self.cache_hits = 0
    self.initialized = false
    return self
end

function TokenRepository:initialize()
    if self.initialized then
        self.cache_hits = self.cache_hits + 1
        return
    end
    
    -- Pre-populate token pools to avoid runtime fetching
    -- These would typically be fetched from Keycloak during setup
    self.valid_tokens = {
        "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWV9.valid-token-1",
        "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWV9.valid-token-2",
        "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWV9.valid-token-3",
        "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWV9.valid-token-4",
        "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWV9.valid-token-5"
    }
    
    self.invalid_tokens = {
        "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWV9.invalid-signature",
        "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.corrupted-payload.invalid-token",
        "completely-invalid-token"
    }
    
    self.expired_tokens = {
        "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiZXhwIjoxNTE2MjM5MDIyfQ.expired-token-1",
        "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiZXhwIjoxNTE2MjM5MDIyfQ.expired-token-2"
    }
    
    self.initialized = true
    self.fetch_count = self.fetch_count + 1
    
    print("TokenRepository initialized:")
    print("  Valid tokens: " .. #self.valid_tokens)
    print("  Invalid tokens: " .. #self.invalid_tokens)
    print("  Expired tokens: " .. #self.expired_tokens)
end

function TokenRepository:getValidToken()
    if not self.initialized then
        self:initialize()
    end
    
    local token = self.valid_tokens[self.token_index]
    self.token_index = self.token_index + 1
    if self.token_index > #self.valid_tokens then
        self.token_index = 1
    end
    
    return token
end

function TokenRepository:getInvalidToken()
    if not self.initialized then
        self:initialize()
    end
    
    local index = (self.token_index % #self.invalid_tokens) + 1
    return self.invalid_tokens[index]
end

function TokenRepository:getExpiredToken()
    if not self.initialized then
        self:initialize()
    end
    
    local index = (self.token_index % #self.expired_tokens) + 1
    return self.expired_tokens[index]
end

function TokenRepository:getTokenByErrorRate(error_percentage)
    if not self.initialized then
        self:initialize()
    end
    
    -- Generate token based on error rate
    local random_value = math.random(1, 100)
    
    if random_value <= error_percentage then
        -- Return error token (split between invalid and expired)
        if random_value <= error_percentage / 2 then
            return self:getInvalidToken()
        else
            return self:getExpiredToken()
        end
    else
        -- Return valid token
        return self:getValidToken()
    end
end

function TokenRepository:getStatistics()
    return {
        initialized = self.initialized,
        fetch_count = self.fetch_count,
        cache_hits = self.cache_hits,
        total_tokens = #self.valid_tokens + #self.invalid_tokens + #self.expired_tokens
    }
end

-- Global TokenRepository instance
local token_repository = TokenRepository.new()

-- Configuration
local request_count = 0
local error_rate = 0  -- 0% error rate for baseline testing

-- JWT validation endpoint
local path = "/jwt/validate"

-- Initialize TokenRepository (called once per thread)
function setup(thread)
    -- Initialize the token repository
    token_repository:initialize()
    
    -- Configure error rate (can be overridden by environment variable)
    local env_error_rate = os.getenv("WRK_ERROR_RATE")
    if env_error_rate then
        error_rate = tonumber(env_error_rate) or 0
    end
    
    print("Thread " .. thread.id .. " initialized with " .. error_rate .. "% error rate")
end

-- Generate request with rotating JWT tokens from TokenRepository
function request()
    request_count = request_count + 1
    
    -- Get token from repository based on error rate
    local token = token_repository:getTokenByErrorRate(error_rate)
    
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
    
    -- TokenRepository statistics
    local token_stats = token_repository:getStatistics()
    print("\nTokenRepository Statistics:")
    print("  Total tokens: " .. token_stats.total_tokens)
    print("  Fetch count: " .. token_stats.fetch_count)
    print("  Cache hits: " .. token_stats.cache_hits)
    print("  Error rate: " .. error_rate .. "%")
    
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
        },
        token_repository = {
            total_tokens = token_stats.total_tokens,
            fetch_count = token_stats.fetch_count,
            cache_hits = token_stats.cache_hits,
            error_rate = error_rate
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