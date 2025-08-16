package de.cuioss.benchmarking.common.detector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BenchmarkTypeDetectorTest {

    @Test
    @DisplayName("Should detect MICRO benchmark type from class name")
    void shouldDetectMicroBenchmark() {
        String microBenchmark = "de.cuioss.benchmark.core.ValidationBenchmark";
        assertEquals(BenchmarkType.MICRO, BenchmarkTypeDetector.detectFromString(microBenchmark));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "de.cuioss.benchmark.integration.HealthBenchmark",
            "de.cuioss.benchmark.quarkus.QuarkusBenchmark"
    })
    @DisplayName("Should detect INTEGRATION benchmark type from class name")
    void shouldDetectIntegrationBenchmark(String benchmarkName) {
        assertEquals(BenchmarkType.INTEGRATION, BenchmarkTypeDetector.detectFromString(benchmarkName));
    }

    @Test
    @DisplayName("Should default to MICRO for unknown package structures")
    void shouldDefaultToMicro() {
        String unknownBenchmark = "com.example.MyBenchmark";
        assertEquals(BenchmarkType.MICRO, BenchmarkTypeDetector.detectFromString(unknownBenchmark));
    }
}
