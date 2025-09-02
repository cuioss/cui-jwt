/*
 * Copyright © 2025 CUI-OpenSource-Software (info@cuioss.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.cuioss.jwt.quarkus.benchmark.benchmarks;

import de.cuioss.jwt.quarkus.benchmark.AbstractBaseBenchmark;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Benchmark class for health endpoints to establish baseline performance.
 * These benchmarks provide a baseline for comparison with JWT validation endpoints.
 *
 * @since 1.0
 */
public class JwtHealthBenchmark extends AbstractBaseBenchmark {

    /**
     * Benchmark for Quarkus health endpoint throughput.
     * Provides baseline throughput measurement without JWT processing.
     */
    @Benchmark @BenchmarkMode({Mode.Throughput, Mode.SampleTime}) public void healthCheckThroughput() throws IOException, InterruptedException {
        HttpRequest request = createRequestForPath("/q/health")
                .GET()
                .build();
        HttpResponse<String> response = sendRequest(request);
        validateResponse(response, 200);
    }
}