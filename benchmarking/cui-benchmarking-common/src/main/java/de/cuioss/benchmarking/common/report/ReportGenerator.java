package de.cuioss.benchmarking.common.report;

import org.openjdk.jmh.results.RunResult;

import java.util.Collection;

/**
 * Generates HTML reports from benchmark results.
 */
public class ReportGenerator {

    /**
     * Generates the main index page for the report.
     *
     * @param results   the collection of benchmark run results
     * @param outputDir the output directory
     */
    public void generateIndexPage(Collection<RunResult> results, String outputDir) {
        // To be implemented
    }

    /**
     * Generates the trends page for the report.
     *
     * @param results   the collection of benchmark run results
     * @param outputDir the output directory
     */
    public void generateTrendsPage(Collection<RunResult> results, String outputDir) {
        // To be implemented
    }
}
