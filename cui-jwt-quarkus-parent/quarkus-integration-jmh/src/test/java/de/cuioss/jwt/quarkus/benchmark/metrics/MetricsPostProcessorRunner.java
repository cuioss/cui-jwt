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
package de.cuioss.jwt.quarkus.benchmark.metrics;

import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Runner to test MetricsPostProcessor on actual benchmark results
 */
class MetricsPostProcessorRunner {

    @Test
    void runParserOnActualResults() throws IOException {
        String resultsDirectory = "target/benchmark-results";

        try {
            MetricsPostProcessor.parseAndExport(resultsDirectory);
            System.out.println("Successfully parsed and exported HTTP metrics from actual benchmark results");
        } catch (Exception e) {
            System.err.println("Failed to parse benchmark results: " + e.getMessage());
            e.printStackTrace();
        }
    }
}