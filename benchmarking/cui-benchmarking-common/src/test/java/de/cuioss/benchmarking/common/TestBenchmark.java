package de.cuioss.benchmarking.common;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

/**
 * A simple benchmark for testing the benchmarking framework itself.
 */
@State(Scope.Benchmark)
public class TestBenchmark {

    @Param({"1"})
    public long cpuTokens;

    /**
     * A dummy benchmark method that consumes CPU tokens to simulate work.
     * @param bh a {@link Blackhole} to prevent dead code elimination.
     */
    @Benchmark
    public void wellKnown(Blackhole bh) {
        bh.consumeCPU(cpuTokens);
    }
}
