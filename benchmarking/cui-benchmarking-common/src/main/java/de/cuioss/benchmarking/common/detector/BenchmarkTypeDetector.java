package de.cuioss.benchmarking.common.detector;

import lombok.experimental.UtilityClass;
import org.openjdk.jmh.results.RunResult;

import java.util.Collection;

/**
 * A utility class to detect the type of benchmark from a collection of results.
 */
@UtilityClass
public class BenchmarkTypeDetector {

    /**
     * Detects the benchmark type from a collection of run results.
     * It inspects the fully qualified name of the benchmark class.
     *
     * @param results the collection of benchmark run results
     * @return the detected {@link BenchmarkType}, defaults to {@link BenchmarkType#MICRO}
     */
    public static BenchmarkType detect(Collection<RunResult> results) {
        return results.stream()
                .map(r -> r.getParams().getBenchmark())
                .findFirst()
                .map(BenchmarkTypeDetector::detectFromString)
                .orElse(BenchmarkType.MICRO);
    }

    /**
     * Detects the benchmark type from a benchmark's fully qualified name.
     *
     * @param benchmarkClassName the fully qualified name of the benchmark class
     * @return the detected {@link BenchmarkType}
     */
    public static BenchmarkType detectFromString(String benchmarkClassName) {
        if (benchmarkClassName.contains(".integration.") || benchmarkClassName.contains(".quarkus.")) {
            return BenchmarkType.INTEGRATION;
        }
        return BenchmarkType.MICRO;
    }
}
