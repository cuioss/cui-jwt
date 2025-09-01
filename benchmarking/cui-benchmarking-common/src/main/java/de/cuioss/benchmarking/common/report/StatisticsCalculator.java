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
package de.cuioss.benchmarking.common.report;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Provides pure statistical computation utilities for benchmark metrics.
 * <p>
 * This class is responsible for all statistical calculations including:
 * <ul>
 *   <li>Basic statistics (min, max, mean, median)</li>
 *   <li>Moving averages</li>
 *   <li>Percentage changes and trends</li>
 *   <li>Standard deviation and variance</li>
 * </ul>
 * <p>
 * Use this class when you need to perform mathematical/statistical operations on benchmark data.
 * For metric-specific calculations (performance scores, grades), use {@link MetricsComputer}.
 * For time-series analysis and trend detection, use {@link TrendDataProcessor}.
 * 
 * @see MetricsComputer for benchmark-specific metric calculations
 * @see TrendDataProcessor for time-series and trend analysis
 */
public final class StatisticsCalculator {

    private StatisticsCalculator() {
        // Utility class with static methods only
    }

    /**
     * Calculates the arithmetic mean (average) of a collection of values.
     *
     * @param values collection of numeric values
     * @return the mean value, or 0.0 if the collection is empty
     * @throws NullPointerException if values is null
     */
    public static double calculateMean(Collection<Double> values) {
        Objects.requireNonNull(values, "Values collection cannot be null");

        if (values.isEmpty()) {
            return 0.0;
        }

        return values.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    /**
     * Finds the minimum value in a collection.
     *
     * @param values collection of numeric values
     * @return the minimum value, or Double.MAX_VALUE if the collection is empty
     * @throws NullPointerException if values is null
     */
    public static double findMin(Collection<Double> values) {
        Objects.requireNonNull(values, "Values collection cannot be null");

        return values.stream()
                .mapToDouble(Double::doubleValue)
                .min()
                .orElse(Double.MAX_VALUE);
    }

    /**
     * Finds the maximum value in a collection.
     *
     * @param values collection of numeric values
     * @return the maximum value, or Double.MIN_VALUE if the collection is empty
     * @throws NullPointerException if values is null
     */
    public static double findMax(Collection<Double> values) {
        Objects.requireNonNull(values, "Values collection cannot be null");

        return values.stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(Double.MIN_VALUE);
    }

    /**
     * Calculates the median value of a collection.
     *
     * @param values collection of numeric values
     * @return the median value, or 0.0 if the collection is empty
     * @throws NullPointerException if values is null
     */
    public static double calculateMedian(Collection<Double> values) {
        Objects.requireNonNull(values, "Values collection cannot be null");

        if (values.isEmpty()) {
            return 0.0;
        }

        List<Double> sorted = values.stream()
                .sorted()
                .toList();

        int size = sorted.size();
        if (size % 2 == 0) {
            return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
        } else {
            return sorted.get(size / 2);
        }
    }

    /**
     * Calculates the percentage change between two values.
     * <p>
     * Formula: ((newValue - oldValue) / oldValue) * 100
     *
     * @param oldValue the original value
     * @param newValue the new value
     * @return the percentage change
     */
    public static double calculatePercentageChange(double oldValue, double newValue) {
        if (oldValue == 0) {
            return newValue == 0 ? 0.0 : 100.0;
        }
        return ((newValue - oldValue) / oldValue) * 100;
    }

    /**
     * Calculates a simple moving average for the most recent N values.
     *
     * @param values list of values (assumed to be in chronological order)
     * @param windowSize the number of most recent values to include
     * @return the moving average
     * @throws NullPointerException if values is null
     * @throws IllegalArgumentException if windowSize is less than 1
     */
    public static double calculateMovingAverage(List<Double> values, int windowSize) {
        Objects.requireNonNull(values, "Values list cannot be null");
        if (windowSize < 1) {
            throw new IllegalArgumentException("Window size must be at least 1");
        }

        if (values.isEmpty()) {
            return 0.0;
        }

        int actualWindow = Math.min(windowSize, values.size());
        List<Double> window = values.size() <= actualWindow
                ? values
                : values.subList(values.size() - actualWindow, values.size());

        return calculateMean(window);
    }

    /**
     * Calculates the standard deviation of a collection of values.
     *
     * @param values collection of numeric values
     * @return the standard deviation, or 0.0 if the collection has less than 2 elements
     * @throws NullPointerException if values is null
     */
    public static double calculateStandardDeviation(Collection<Double> values) {
        Objects.requireNonNull(values, "Values collection cannot be null");

        if (values.size() < 2) {
            return 0.0;
        }

        double mean = calculateMean(values);
        double variance = values.stream()
                .mapToDouble(value -> Math.pow(value - mean, 2))
                .average()
                .orElse(0.0);

        return Math.sqrt(variance);
    }

    /**
     * Determines the trend direction based on percentage change and a stability threshold.
     *
     * @param percentageChange the percentage change value
     * @param stabilityThreshold the threshold (as a percentage) below which the trend is considered stable
     * @return "up", "down", or "stable"
     */
    public static String determineTrendDirection(double percentageChange, double stabilityThreshold) {
        if (Math.abs(percentageChange) < stabilityThreshold) {
            return "stable";
        }
        return percentageChange > 0 ? "up" : "down";
    }

    /**
     * Calculates the coefficient of variation (relative standard deviation).
     * <p>
     * This is useful for comparing the relative variability of datasets with different means.
     *
     * @param values collection of numeric values
     * @return the coefficient of variation as a percentage, or 0.0 if mean is 0
     * @throws NullPointerException if values is null
     */
    public static double calculateCoefficientOfVariation(Collection<Double> values) {
        Objects.requireNonNull(values, "Values collection cannot be null");

        double mean = calculateMean(values);
        if (mean == 0) {
            return 0.0;
        }

        double stdDev = calculateStandardDeviation(values);
        return (stdDev / mean) * 100;
    }

    /**
     * Statistical summary containing basic statistics for a dataset.
     */
    public static class Statistics {
        private final double min;
        private final double max;
        private final double mean;
        private final double median;
        private final double stdDev;
        private final int count;

        public Statistics(double min, double max, double mean, double median, double stdDev, int count) {
            this.min = min;
            this.max = max;
            this.mean = mean;
            this.median = median;
            this.stdDev = stdDev;
            this.count = count;
        }

        public double getMin() {
            return min;
        }

        public double getMax() {
            return max;
        }

        public double getMean() {
            return mean;
        }

        public double getMedian() {
            return median;
        }

        public double getStdDev() {
            return stdDev;
        }

        public int getCount() {
            return count;
        }
    }

    /**
     * Computes comprehensive statistics for a dataset.
     *
     * @param values collection of numeric values
     * @return a Statistics object containing min, max, mean, median, standard deviation, and count
     * @throws NullPointerException if values is null
     */
    public static Statistics computeStatistics(Collection<Double> values) {
        Objects.requireNonNull(values, "Values collection cannot be null");

        return new Statistics(
                findMin(values),
                findMax(values),
                calculateMean(values),
                calculateMedian(values),
                calculateStandardDeviation(values),
                values.size()
        );
    }
}