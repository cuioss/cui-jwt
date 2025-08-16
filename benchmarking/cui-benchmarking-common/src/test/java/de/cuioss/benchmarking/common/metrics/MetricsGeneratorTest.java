package de.cuioss.benchmarking.common.metrics;

import com.google.gson.Gson;
import de.cuioss.benchmarking.common.TestBenchmark;
import de.cuioss.benchmarking.common.gson.GsonProvider;
import de.cuioss.test.juli.EnableTestLogger;
import de.cuioss.test.juli.TestLogLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

@EnableTestLogger(rootLevel = TestLogLevel.INFO)
class MetricsGeneratorTest {

    private MetricsGenerator metricsGenerator;

    @BeforeEach
    void setUp() {
        metricsGenerator = new MetricsGenerator();
    }

    @Test
    void shouldGenerateMetricsJson(@TempDir Path tempDir) throws IOException, RunnerException {
        // Arrange
        Collection<RunResult> results = runTestBenchmark(1000);
        String outputDir = tempDir.toString();

        // Act
        metricsGenerator.generateMetricsJson(results, outputDir);

        // Assert
        Path metricsFile = tempDir.resolve("metrics.json");
        assertTrue(Files.exists(metricsFile));

        Gson gson = GsonProvider.getGson();
        PerformanceMetrics metrics = gson.fromJson(new FileReader(metricsFile.toFile()), PerformanceMetrics.class);

        assertEquals(1, metrics.benchmarkCount());
        assertNotNull(metrics.timestamp());
        assertTrue(metrics.averageThroughput() > 0);
        assertEquals(1, metrics.benchmarks().size());

        Path individualMetricsFile = tempDir.resolve("TestBenchmark-metrics.json");
        assertTrue(Files.exists(individualMetricsFile));
    }

    private Collection<RunResult> runTestBenchmark(long cpuTokens) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(TestBenchmark.class.getSimpleName())
                .warmupIterations(1)
                .measurementIterations(1)
                .forks(1)
                .param("cpuTokens", String.valueOf(cpuTokens))
                .build();

        return new Runner(opt).run();
    }
}
