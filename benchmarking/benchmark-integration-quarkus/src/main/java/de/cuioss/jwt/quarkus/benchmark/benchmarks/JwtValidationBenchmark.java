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

import de.cuioss.jwt.quarkus.benchmark.AbstractIntegrationBenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Benchmark class for JWT validation endpoints against live Quarkus service.
 * Tests successful JWT validation scenario.
 *
 * @since 1.0
 */
public class JwtValidationBenchmark extends AbstractIntegrationBenchmark {

    public static final String PATH = "/jwt/validate";

    /**
     * Benchmark for successful JWT validation with valid tokens.
     * Measures throughput and latency of the /jwt/validate endpoint.
     */
    @Benchmark @BenchmarkMode({Mode.Throughput, Mode.SampleTime}) public void validateJwtThroughput() throws IOException, InterruptedException {
        HttpRequest request = createAuthenticatedRequest(PATH)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = sendRequest(request);
        validateResponse(response, 200);
    }
}