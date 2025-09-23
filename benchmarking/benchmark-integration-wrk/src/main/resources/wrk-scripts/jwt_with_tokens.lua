-- WRK script for JWT validation benchmark with pre-fetched Keycloak tokens
-- REQUIRES: Pre-fetched tokens using fetch_tokens.sh script
-- Token expiration will be visible as 401/403 errors

-- Token storage
local tokens = {}
local token_index = 0

-- Statistics
local stats = {
    requests = 0,
    errors = 0,
    unauthorized = 0,
    success = 0
}


-- Load pre-fetched tokens from file (REQUIRED - will fail if not available)
local function init_tokens()
    local token_file = os.getenv("TOKEN_FILE") or "target/tokens.txt"
    local file = io.open(token_file, "r")

    if not file then
        error("FATAL: Token file not found: " .. token_file ..
              "\nPlease run: bash src/main/resources/wrk-scripts/fetch_tokens.sh")
    end

    print("Loading pre-fetched tokens from: " .. token_file)
    for line in file:lines() do
        if line and #line > 0 then
            table.insert(tokens, line)
        end
    end
    file:close()

    if #tokens == 0 then
        error("FATAL: No tokens found in file: " .. token_file)
    end

    print(string.format("Loaded %d real JWT tokens", #tokens))
    print("NOTE: Token expiration will be visible as 401/403 errors during benchmark")
end

-- Initialize tokens at module load time (before threads start)
init_tokens()

-- WRK setup function - called once at the beginning
function setup(thread)
    thread:set("id", thread.id)
    return thread
end

-- WRK init function - called once per thread
function init(args)
    -- Each thread uses the shared tokens table
end

-- WRK request function - called for each request
function request()
    -- Get next token (round-robin)
    if #tokens == 0 then
        print("ERROR: No tokens available!")
        return nil
    end

    token_index = (token_index % #tokens) + 1
    local token = tokens[token_index]

    -- Build the request
    wrk.method = "POST"
    wrk.path = "/jwt/validate"
    wrk.headers["Authorization"] = "Bearer " .. token
    wrk.headers["Content-Type"] = "application/json"
    wrk.headers["Accept"] = "application/json"
    wrk.headers["User-Agent"] = "wrk-benchmark/1.0"
    wrk.headers["Connection"] = "keep-alive"

    stats.requests = stats.requests + 1

    return wrk.format(nil, nil, nil, "")
end

-- WRK response function - called for each response
function response(status, headers, body)
    if status == 200 or status == 201 then
        stats.success = stats.success + 1
    elseif status == 401 or status == 403 then
        stats.unauthorized = stats.unauthorized + 1
        if stats.unauthorized == 1 then
            print("⚠️  TOKEN EXPIRATION DETECTED - Getting 401/403 responses")
            print("   Tokens may have expired. Re-run fetch_tokens.sh for fresh tokens.")
        end
    elseif status == 404 then
        if stats.errors <= 5 then
            print(string.format("Not Found: JWT validation endpoint not accessible at /jwt/validate"))
        end
        stats.errors = stats.errors + 1
    else
        stats.errors = stats.errors + 1
        if stats.errors <= 5 then
            print(string.format("Error: HTTP %d - %s", status, body and string.sub(body, 1, 100) or "No body"))
        end
    end
end

-- WRK done function - called when benchmark completes
function done(summary, latency, requests)
    print("=== JWT Validation Benchmark Results (REAL TOKENS) ===")
    print(string.format("Token pool size:   %d", #tokens))
    print(string.format("Total requests:    %d", stats.requests))
    print(string.format("Successful:        %d (%.1f%%)", stats.success, (stats.success * 100.0) / math.max(1, stats.requests)))
    print(string.format("Unauthorized:      %d (%.1f%%)", stats.unauthorized, (stats.unauthorized * 100.0) / math.max(1, stats.requests)))
    print(string.format("Errors:            %d (%.1f%%)", stats.errors, (stats.errors * 100.0) / math.max(1, stats.requests)))
    print(string.format("Duration:          %.2fs", summary.duration / 1000000))
    print(string.format("Requests/sec:      %.2f", summary.requests / (summary.duration / 1000000)))
    print(string.format("Avg Latency:       %.2fms", latency.mean / 1000))
    print(string.format("Max Latency:       %.2fms", latency.max / 1000))
    print(string.format("50th percentile:   %.2fms", latency:percentile(50) / 1000))
    print(string.format("90th percentile:   %.2fms", latency:percentile(90) / 1000))
    print(string.format("99th percentile:   %.2fms", latency:percentile(99) / 1000))
end