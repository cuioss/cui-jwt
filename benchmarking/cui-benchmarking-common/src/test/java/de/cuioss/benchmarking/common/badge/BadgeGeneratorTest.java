package de.cuioss.benchmarking.common.badge;

import com.google.gson.Gson;
import de.cuioss.benchmarking.common.TestBenchmark;
import de.cuioss.benchmarking.common.detector.BenchmarkType;
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

class BadgeGeneratorTest {

    private BadgeGenerator badgeGenerator;

    @BeforeEach
    void setUp() {
        badgeGenerator = new BadgeGenerator();
    }

    @Test
    void shouldGeneratePerformanceBadge(@TempDir Path tempDir) throws IOException, RunnerException {
        // Arrange
        Collection<RunResult> results = runTestBenchmark(1);
        String outputDir = tempDir.toString();

        // Act
        badgeGenerator.generatePerformanceBadge(results, BenchmarkType.MICRO, outputDir);

        // Assert
        Path badgeFile = tempDir.resolve("performance-badge.json");
        assertTrue(Files.exists(badgeFile));

        Gson gson = new Gson();
        Badge badge = gson.fromJson(new FileReader(badgeFile.toFile()), Badge.class);

        assertEquals(1, badge.getSchemaVersion());
        assertEquals("Performance Score", badge.getLabel());
        assertNotNull(badge.getMessage());
        assertTrue(badge.getMessage().contains("ops/s"));
        // A low number of CPU tokens should result in a high score -> brightgreen
        assertEquals("brightgreen", badge.getColor());
    }

    @Test
    void shouldGenerateRedBadgeForLowScore(@TempDir Path tempDir) throws IOException, RunnerException {
        // Arrange
        Collection<RunResult> results = runTestBenchmark(1_000_000);
        String outputDir = tempDir.toString();

        // Act
        badgeGenerator.generatePerformanceBadge(results, BenchmarkType.MICRO, outputDir);

        // Assert
        Path badgeFile = tempDir.resolve("performance-badge.json");
        assertTrue(Files.exists(badgeFile));

        Gson gson = new Gson();
        Badge badge = gson.fromJson(new FileReader(badgeFile.toFile()), Badge.class);

        // A high number of CPU tokens should result in a low score -> red
        assertEquals("red", badge.getColor());
    }

    @Test
    void shouldGenerateLastRunBadge(@TempDir Path tempDir) throws IOException {
        // Arrange
        String outputDir = tempDir.toString();

        // Act
        badgeGenerator.generateLastRunBadge(outputDir);

        // Assert
        Path badgeFile = tempDir.resolve("last-run-badge.json");
        assertTrue(Files.exists(badgeFile));

        Gson gson = new Gson();
        Badge badge = gson.fromJson(new FileReader(badgeFile.toFile()), Badge.class);

        assertEquals(1, badge.getSchemaVersion());
        assertEquals("Last Run", badge.getLabel());
        assertTrue(badge.getMessage().endsWith(" UTC"));
        assertEquals("blue", badge.getColor());
    }

    @Test
    void shouldGenerateTrendBadge(@TempDir Path tempDir) throws IOException, RunnerException {
        // Arrange
        Collection<RunResult> results = runTestBenchmark(1000);
        String outputDir = tempDir.toString();

        // Act
        badgeGenerator.generateTrendBadge(results, BenchmarkType.MICRO, outputDir);

        // Assert
        Path badgeFile = tempDir.resolve("trend-badge.json");
        assertTrue(Files.exists(badgeFile));

        Gson gson = new Gson();
        Badge badge = gson.fromJson(new FileReader(badgeFile.toFile()), Badge.class);

        assertEquals(1, badge.getSchemaVersion());
        assertEquals("Performance Trend", badge.getLabel());
        assertEquals("stable", badge.getMessage());
        assertEquals("blue", badge.getColor());
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
