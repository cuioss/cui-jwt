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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Abstract base class for MetricsPostProcessor tests providing common test infrastructure
 * and parameterized test methods to reduce redundancy across test classes.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS) public abstract class AbstractMetricsProcessorTest {

    @TempDir
    protected Path tempDir;

    protected Gson gson;

    @BeforeEach protected void setUp() throws IOException {
        gson = new GsonBuilder().create();
        onSetUp();
    }

    /**
     * Hook for subclasses to perform additional setup
     */
    protected void onSetUp() throws IOException {
        // Hook for subclasses
    }

    /**
     * Parameterized test for file existence verification
     */
    @ParameterizedTest @MethodSource("provideFileExistenceTestCases") public void shouldCreateExpectedOutputFile(String testName, TestFixture fixture) throws IOException {
        // Arrange
        String inputFile = createTestDataFile(fixture.getInputData());

        // Act
        processMetrics(inputFile, tempDir.toString(), fixture);

        // Assert
        File outputFile = new File(tempDir.toFile(), fixture.getExpectedOutputFileName());
        assertTrue(outputFile.exists(),
                "Test '%s' should create %s".formatted(testName, fixture.getExpectedOutputFileName()));
    }

    /**
     * Parameterized test for JSON structure validation
     */
    @ParameterizedTest @MethodSource("provideJsonStructureTestCases") public void shouldGenerateCorrectJsonStructure(String testName, TestFixture fixture, String[] expectedKeys) throws IOException {
        // Arrange
        String inputFile = createTestDataFile(fixture.getInputData());

        // Act
        processMetrics(inputFile, tempDir.toString(), fixture);

        // Assert
        File outputFile = new File(tempDir.toFile(), fixture.getExpectedOutputFileName());
        try (FileReader reader = new FileReader(outputFile)) {
            @SuppressWarnings("unchecked") Map<String, Object> metrics = gson.fromJson(reader, Map.class);

            for (String key : expectedKeys) {
                assertTrue(metrics.containsKey(key),
                        "Test '%s' output should contain key: %s".formatted(testName, key));
            }
        }
    }

    /**
     * Parameterized test for numeric value validation
     */
    @ParameterizedTest @MethodSource("provideNumericValidationTestCases") public void shouldValidateNumericValues(String testName, TestFixture fixture, NumericValidation validation) throws IOException {
        // Arrange
        String inputFile = createTestDataFile(fixture.getInputData());

        // Act
        processMetrics(inputFile, tempDir.toString(), fixture);

        // Assert
        File outputFile = new File(tempDir.toFile(), fixture.getExpectedOutputFileName());
        try (FileReader reader = new FileReader(outputFile)) {
            @SuppressWarnings("unchecked") Map<String, Object> metrics = gson.fromJson(reader, Map.class);

            Object value = getNestedValue(metrics, validation.getPath());
            assertInstanceOf(Number.class, value,
                    "Test '%s': Value at path %s should be numeric".formatted(testName, validation.getPath()));

            double numericValue = ((Number) value).doubleValue();
            validation.validate(testName, numericValue);
        }
    }

    /**
     * Common test for timestamp handling
     */
    @ParameterizedTest @MethodSource("provideTimestampTestCases") public void shouldHandleTimestampCorrectly(String testName, TestFixture fixture, String timestampPath) throws IOException {
        // Arrange
        String inputFile = createTestDataFile(fixture.getInputData());
        Instant testTimestamp = Instant.parse("2025-08-01T12:00:00.000Z");

        // Act
        processMetricsWithTimestamp(inputFile, tempDir.toString(), fixture, testTimestamp);

        // Assert
        File outputFile = new File(tempDir.toFile(), fixture.getExpectedOutputFileName());
        try (FileReader reader = new FileReader(outputFile)) {
            @SuppressWarnings("unchecked") Map<String, Object> metrics = gson.fromJson(reader, Map.class);

            Object timestamp = getNestedValue(metrics, timestampPath);
            assertEquals(testTimestamp.toString(), timestamp,
                    "Test '%s' should have correct timestamp".formatted(testName));
        }
    }

    /**
     * Common test for exception handling
     * Only runs if provideExceptionTestCases returns non-empty stream
     */
    @ParameterizedTest @MethodSource("provideExceptionTestCases") public void shouldHandleExceptions(String testName, ExceptionTestCase testCase) {
        if (testCase != null) {
            assertThrows(testCase.getExpectedException(),
                    () -> processMetrics(testCase.getInvalidInput(), tempDir.toString(), testCase.getFixture()),
                    "Test '%s' should throw %s".formatted(testName, testCase.getExpectedException().getSimpleName()));
        }
    }

    /**
     * Creates a test data file with the given content
     */
    protected String createTestDataFile(String content) throws IOException {
        File testFile = new File(tempDir.toFile(), "test-data-" + System.currentTimeMillis() + ".json");
        try (FileWriter writer = new FileWriter(testFile)) {
            writer.write(content);
        }
        return testFile.getAbsolutePath();
    }

    /**
     * Gets a nested value from a map using dot notation path
     */
    protected Object getNestedValue(Map<String, Object> map, String path) {
        String[] parts = path.split("\\.");
        Object current = map;

        for (String part : parts) {
            if (current instanceof Map) {
                @SuppressWarnings("unchecked") Map<String, Object> currentMap = (Map<String, Object>) current;
                current = currentMap.get(part);
            } else {
                return null;
            }
        }

        return current;
    }

    /**
     * Abstract method for processing metrics - must be implemented by subclasses
     */
    protected abstract void processMetrics(String inputFile, String outputDir, TestFixture fixture) throws IOException;

    /**
     * Abstract method for processing metrics with timestamp - must be implemented by subclasses
     */
    protected abstract void processMetricsWithTimestamp(String inputFile, String outputDir, TestFixture fixture, Instant timestamp) throws IOException;

    /**
     * Provides test cases for file existence tests - must be implemented by subclasses
     */
    protected abstract Stream<Arguments> provideFileExistenceTestCases();

    /**
     * Provides test cases for JSON structure tests - must be implemented by subclasses
     */
    protected abstract Stream<Arguments> provideJsonStructureTestCases();

    /**
     * Provides test cases for numeric validation tests - can be overridden by subclasses
     */
    protected Stream<Arguments> provideNumericValidationTestCases() {
        return Stream.empty();
    }

    /**
     * Provides test cases for timestamp tests - can be overridden by subclasses
     */
    protected Stream<Arguments> provideTimestampTestCases() {
        return Stream.empty();
    }

    /**
     * Provides test cases for exception tests - can be overridden by subclasses
     * Returns a dummy null case if no real test cases, to satisfy @ParameterizedTest requirement
     */
    protected Stream<Arguments> provideExceptionTestCases() {
        // Return null case to satisfy parameterized test requirement for at least one argument
        return Stream.of(Arguments.of("NoExceptionTests", null));
    }

    /**
     * Test fixture containing test data and expected results
     */
    public static class TestFixture {
        private final String inputData;
        private final String expectedOutputFileName;
        private final ProcessorType processorType;

        public TestFixture(String inputData, String expectedOutputFileName, ProcessorType processorType) {
            this.inputData = inputData;
            this.expectedOutputFileName = expectedOutputFileName;
            this.processorType = processorType;
        }

        public String getInputData() {
            return inputData;
        }

        public String getExpectedOutputFileName() {
            return expectedOutputFileName;
        }

        public ProcessorType getProcessorType() {
            return processorType;
        }
    }

    /**
     * Enum for different processor types
     */
    public enum ProcessorType {
        HTTP_METRICS,
        QUARKUS_METRICS,
        COMPREHENSIVE
    }

    /**
     * Numeric validation helper
     */
    public static class NumericValidation {
        private final String path;
        private final double minValue;
        private final double maxValue;

        public NumericValidation(String path, double minValue, double maxValue) {
            this.path = path;
            this.minValue = minValue;
            this.maxValue = maxValue;
        }

        public String getPath() {
            return path;
        }

        public void validate(String testName, double value) {
            assertTrue(value >= minValue && value <= maxValue,
                    "Test '%s': Value %.2f should be between %.2f and %.2f".formatted(
                            testName, value, minValue, maxValue));
        }
    }

    /**
     * Exception test case
     */
    public static class ExceptionTestCase {
        private final String invalidInput;
        private final Class<? extends Exception> expectedException;
        private final TestFixture fixture;

        public ExceptionTestCase(String invalidInput, Class<? extends Exception> expectedException, TestFixture fixture) {
            this.invalidInput = invalidInput;
            this.expectedException = expectedException;
            this.fixture = fixture;
        }

        public String getInvalidInput() {
            return invalidInput;
        }

        public Class<? extends Exception> getExpectedException() {
            return expectedException;
        }

        public TestFixture getFixture() {
            return fixture;
        }
    }
}