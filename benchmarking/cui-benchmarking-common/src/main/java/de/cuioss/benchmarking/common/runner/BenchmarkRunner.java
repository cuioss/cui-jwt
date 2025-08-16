package de.cuioss.benchmarking.common.runner;

import de.cuioss.benchmarking.common.processor.BenchmarkResultProcessor;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Collection;

/**
 * A utility class for running JMH benchmarks and processing the results.
 */
public class BenchmarkRunner {

    /**
     * The main entry point for running benchmarks.
     *
     * @param args command line arguments
     * @throws Exception if an error occurs during benchmark execution
     */
    public static void main(String[] args) throws Exception {
        String outputDir = System.getProperty("benchmark.output.dir", "target/benchmark-results");
        String resultFile = outputDir + "/raw-result.json";

        Options options = new OptionsBuilder()
                .include(System.getProperty("benchmark.include", ".*"))
                .forks(Integer.getInteger("benchmark.forks", 1))
                .resultFormat(ResultFormatType.JSON)
                .result(resultFile)
                .build();

        Collection<RunResult> results = new Runner(options).run();

        if (Boolean.getBoolean("benchmark.process.results")) {
            BenchmarkResultProcessor processor = new BenchmarkResultProcessor();
            processor.processResults(results, outputDir);
        }
    }
}
