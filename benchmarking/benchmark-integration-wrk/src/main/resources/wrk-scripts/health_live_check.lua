-- WRK script for health check benchmark
-- Simple GET requests to the health endpoint

-- Statistics
local stats = {
    requests = 0,
    errors = 0,
    success = 0
}

-- WRK request function - called for each request
function request()
    wrk.method = "GET"
    wrk.path = "/q/health/live"
    wrk.headers["Accept"] = "application/json"
    wrk.headers["User-Agent"] = "wrk-benchmark/1.0"
    wrk.headers["Connection"] = "keep-alive"

    stats.requests = stats.requests + 1

    return wrk.format(nil, nil, nil, nil)
end

-- WRK response function - called for each response
function response(status, headers, body)
    if status == 200 then
        stats.success = stats.success + 1
    else
        stats.errors = stats.errors + 1
        if stats.errors <= 5 then
            print(string.format("Error: HTTP %d - %s", status, body and string.sub(body, 1, 100) or "No body"))
        end
    end
end

-- WRK done function - called when benchmark completes
-- Removed custom output to avoid duplication with WRK's native output
-- WRK already provides all necessary statistics when run with --latency flag