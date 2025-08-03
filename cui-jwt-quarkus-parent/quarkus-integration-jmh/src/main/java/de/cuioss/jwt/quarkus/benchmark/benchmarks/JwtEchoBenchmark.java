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
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Benchmark class for echo endpoints to measure network and serialization baseline.
 * These benchmarks provide network overhead baseline for comparison with JWT operations.
 *
 * @since 1.0
 */
public class JwtEchoBenchmark extends AbstractBaseBenchmark {

    /**
     * Simple echo benchmark for network baseline throughput.
     * Tests basic HTTP request/response without any business logic.
     */
    @Benchmark
    @BenchmarkMode({Mode.Throughput, Mode.SampleTime})
    public void echoGetThroughput() throws IOException, InterruptedException {
        String jsonPayload = "{\"data\": {\"message\": \"benchmark-test\"}}";

        HttpRequest request = createBaseRequest("/jwt/echo")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = sendRequest(request);
        validateResponse(response, 200);
    }

}