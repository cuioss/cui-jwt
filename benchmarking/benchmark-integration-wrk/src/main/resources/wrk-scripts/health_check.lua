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
    wrk.path = "/q/health"
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
function done(summary, latency, requests)
    print("=== Health Check Benchmark Results ===")
    print(string.format("Total requests:    %d", stats.requests))
    print(string.format("Successful:        %d (%.1f%%)", stats.success, (stats.success * 100.0) / stats.requests))
    print(string.format("Errors:            %d (%.1f%%)", stats.errors, (stats.errors * 100.0) / stats.requests))
    print(string.format("Duration:          %.2fs", summary.duration / 1000000))
    print(string.format("Requests/sec:      %.2f", summary.requests / (summary.duration / 1000000)))
    print(string.format("Avg Latency:       %.2fms", latency.mean / 1000))
    print(string.format("Max Latency:       %.2fms", latency.max / 1000))
    print(string.format("50th percentile:   %.2fms", latency:percentile(50) / 1000))
    print(string.format("90th percentile:   %.2fms", latency:percentile(90) / 1000))
    print(string.format("99th percentile:   %.2fms", latency:percentile(99) / 1000))
end