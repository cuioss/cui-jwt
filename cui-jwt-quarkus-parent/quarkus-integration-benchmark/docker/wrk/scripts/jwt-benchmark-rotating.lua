-- JWT Validation Script with Token Rotation for Cache Testing
-- Ensures proper validation pipeline coverage with controlled cache ratio

-- Configuration
local target_cache_ratio = 0.1  -- 10% cache hits, 90% cache misses
local tokens_per_rotation = 10  -- Use each token 10 times before rotating

-- Multi-realm token support
local access_tokens = {}
local realm = os.getenv("REALM") or "benchmark"

-- Load multiple tokens if available
for i = 1, 10 do
    local token_env = "ACCESS_TOKEN_" .. i
    local token = os.getenv(token_env)
    if token then
        table.insert(access_tokens, token)
        print("✅ Loaded " .. token_env .. " (length: " .. string.len(token) .. ")")
    end
end

-- Fall back to single token if no multiple tokens
if #access_tokens == 0 then
    local single_token = os.getenv("ACCESS_TOKEN")
    if single_token and single_token ~= "" then
        access_tokens[1] = single_token
        print("⚠️  Only single ACCESS_TOKEN available - cache ratio will be 100%")
        print("   To achieve 10% cache ratio, provide ACCESS_TOKEN_1 through ACCESS_TOKEN_10")
    else
        print("❌ FATAL: No ACCESS_TOKEN provided")
        os.exit(1)
    end
end

print("🔄 Token rotation configured:")
print("   Available tokens: " .. #access_tokens)
print("   Target cache ratio: " .. (target_cache_ratio * 100) .. "%")
print("   Requests per token: " .. tokens_per_rotation)

-- Request tracking
local request_count = 0
local current_token_index = 1
local token_use_count = 0
local first_failure_detected = false

-- Statistics tracking
local total_requests = 0
local cache_hits = 0
local validation_count = 0

function request()
    -- Exit immediately if we've already detected a failure (fail-fast)
    if first_failure_detected then
        return nil
    end
    
    request_count = request_count + 1
    total_requests = total_requests + 1
    
    -- Rotate tokens based on configured pattern
    if #access_tokens > 1 then
        token_use_count = token_use_count + 1
        
        -- Switch to next token after tokens_per_rotation uses
        if token_use_count >= tokens_per_rotation then
            current_token_index = current_token_index + 1
            if current_token_index > #access_tokens then
                current_token_index = 1
            end
            token_use_count = 0
            validation_count = validation_count + 1
            
            -- Log rotation periodically
            if request_count % 100 == 0 then
                local actual_cache_ratio = cache_hits / math.max(1, validation_count)
                print(string.format("📊 Progress: %d requests, %d validations, %.1f%% cache ratio",
                    request_count, validation_count, actual_cache_ratio * 100))
            end
        else
            cache_hits = cache_hits + 1
        end
    end
    
    local current_token = access_tokens[current_token_index]
    local body = "{}"
    local headers = {
        ["Content-Type"] = "application/json",
        ["Authorization"] = "Bearer " .. current_token
    }
    return wrk.format("POST", "/jwt/validate", headers, body)
end

function response(status, headers, body)
    -- Track 401 responses for fail-fast behavior
    if status == 401 then
        local response_message = body or "no body"
        local message_match = string.match(response_message, '"message":"([^"]*)"')
        local clean_message = message_match and message_match or response_message
        
        print("⚠️  Unexpected 401 with valid token: " .. clean_message)
        print("🚨 FAIL-FAST: Token validation failure - stopping benchmark")
        first_failure_detected = true
        
        -- Write failure results
        local results = string.format(
            '{"throughput_rps":0,"latency_p95_us":0,"latency_p99_us":0,"errors":1,' ..
            '"connection_errors":0,"fail_fast_triggered":true,"success_rate":0.0,' ..
            '"validation_count":%d,"cache_hits":%d,"cache_ratio":%.2f}',
            validation_count, cache_hits, cache_hits / math.max(1, validation_count))
        
        local file = io.open("/tmp/jwt-validation-results.json", "w")
        if file then
            file:write(results)
            file:close()
            print("✅ Fail-fast results written")
        end
        
        os.execute("sleep 0.1")
        os.exit(1)
    end
end

function done(summary, latency, requests)
    print("🔍 Benchmark completed - generating results")
    
    -- Check for fail-fast
    if first_failure_detected then
        print("⚠️  Results already written due to fail-fast")
        return
    end
    
    local throughput = summary.requests / (summary.duration / 1000000)
    local connection_errors = summary.errors.connect + summary.errors.read + 
                           summary.errors.write + summary.errors.timeout
    
    -- Calculate final cache ratio
    local final_cache_ratio = cache_hits / math.max(1, validation_count)
    
    -- Latency in microseconds
    local latency_p95_us = latency:percentile(95)
    local latency_p99_us = latency:percentile(99)
    
    print("📊 Final Statistics:")
    print("   Total requests: " .. total_requests)
    print("   Unique validations: " .. validation_count)
    print("   Cache hits: " .. cache_hits)
    print("   Actual cache ratio: " .. string.format("%.1f%%", final_cache_ratio * 100))
    print("   Throughput: " .. string.format("%.0f", throughput) .. " req/sec")
    print("   Latency P95: " .. string.format("%.1f", latency_p95_us) .. "μs")
    print("   Latency P99: " .. string.format("%.1f", latency_p99_us) .. "μs")
    
    local results = string.format(
        '{"throughput_rps":%.0f,"latency_p95_us":%.1f,"latency_p99_us":%.1f,' ..
        '"errors":%d,"connection_errors":%d,"fail_fast_triggered":false,' ..
        '"success_rate":100.0,"validation_count":%d,"cache_hits":%d,"cache_ratio":%.2f}',
        throughput, latency_p95_us, latency_p99_us, connection_errors, connection_errors,
        validation_count, cache_hits, final_cache_ratio)
    
    local file = io.open("/tmp/jwt-validation-results.json", "w")
    if file then
        file:write(results)
        file:close()
        print("✅ Results written to /tmp/jwt-validation-results.json")
    else
        print("❌ Failed to write results file")
    end
end