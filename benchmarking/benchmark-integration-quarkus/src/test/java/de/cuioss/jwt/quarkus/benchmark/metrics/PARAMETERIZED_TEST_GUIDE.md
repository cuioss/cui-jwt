# MetricsPostProcessor Parameterized Test Guide

## Overview

This guide explains the parameterized testing approach for MetricsPostProcessor tests, which significantly reduces code duplication and improves test maintainability.

## Architecture

### Core Components

1. **AbstractMetricsProcessorTest**: Base class providing common test infrastructure
2. **MetricsTestDataConstants**: Centralized test data constants
3. **Test Fixtures**: Reusable test data and configuration objects
4. **Parameterized Test Methods**: JUnit 5 parameterized tests for common scenarios

## How It Works

### 1. Base Class Structure

The `AbstractMetricsProcessorTest` provides:
- Common setup with `@TempDir` and Gson initialization
- Parameterized test methods for common test patterns
- Helper methods for test data creation and validation
- Abstract methods that subclasses must implement

### 2. Test Data Centralization

`MetricsTestDataConstants` contains:
- Standard benchmark JSON data
- Prometheus metrics samples
- Edge case test data (empty, invalid, mixed modes)
- Multi-iteration test data

### 3. Parameterized Test Categories

#### File Existence Tests
Verify that processors create expected output files:
```java
@ParameterizedTest
@MethodSource("provideFileExistenceTestCases")
public void shouldCreateExpectedOutputFile(String testName, TestFixture fixture)
```

#### JSON Structure Tests
Validate the structure of generated JSON:
```java
@ParameterizedTest
@MethodSource("provideJsonStructureTestCases")
public void shouldGenerateCorrectJsonStructure(String testName, TestFixture fixture, String[] expectedKeys)
```

#### Numeric Validation Tests
Verify numeric values fall within expected ranges:
```java
@ParameterizedTest
@MethodSource("provideNumericValidationTestCases")
public void shouldValidateNumericValues(String testName, TestFixture fixture, NumericValidation validation)
```

#### Timestamp Tests
Validate timestamp handling:
```java
@ParameterizedTest
@MethodSource("provideTimestampTestCases")
public void shouldHandleTimestampCorrectly(String testName, TestFixture fixture, String timestampPath)
```

#### Exception Tests
Verify proper exception handling:
```java
@ParameterizedTest
@MethodSource("provideExceptionTestCases")
public void shouldHandleExceptions(String testName, ExceptionTestCase testCase)
```

## Creating a New Test Class

### Step 1: Extend AbstractMetricsProcessorTest

```java
class MyMetricsProcessorTest extends AbstractMetricsProcessorTest {
    // Your test class implementation
}
```

### Step 2: Implement Abstract Methods

```java
@Override
protected void processMetrics(String inputFile, String outputDir, TestFixture fixture) throws IOException {
    // Create and run your processor
    MyMetricsProcessor processor = new MyMetricsProcessor(inputFile, outputDir);
    processor.process();
}

@Override
protected void processMetricsWithTimestamp(String inputFile, String outputDir, TestFixture fixture, Instant timestamp) throws IOException {
    MyMetricsProcessor processor = new MyMetricsProcessor(inputFile, outputDir);
    processor.processWithTimestamp(timestamp);
}
```

### Step 3: Provide Test Cases

```java
@Override
protected Stream<Arguments> provideFileExistenceTestCases() {
    return Stream.of(
        Arguments.of("Test case name", 
            new TestFixture(TEST_DATA_CONSTANT, "expected-output.json", ProcessorType.MY_TYPE))
    );
}

@Override
protected Stream<Arguments> provideJsonStructureTestCases() {
    return Stream.of(
        Arguments.of("Test case name",
            new TestFixture(TEST_DATA_CONSTANT, "output.json", ProcessorType.MY_TYPE),
            new String[]{"expectedKey1", "expectedKey2"})
    );
}
```

### Step 4: Add Specific Tests (Optional)

For tests that don't fit the parameterized patterns:

```java
@Test
void mySpecificTest() {
    // Your specific test implementation
}
```

## Benefits

1. **Reduced Duplication**: Common test patterns are implemented once
2. **Consistent Testing**: All processors tested with same patterns
3. **Easy Extension**: New test cases added via method sources
4. **Better Maintainability**: Changes to test patterns made in one place
5. **Clear Test Names**: Parameterized tests show descriptive names
6. **Reusable Test Data**: Centralized constants prevent duplication

## Test Coverage

The parameterized approach covers:
- File creation verification
- JSON structure validation
- Numeric value ranges
- Timestamp formatting
- Exception handling
- Performance metrics validation
- Multi-iteration data aggregation
- Mode filtering (sample vs throughput)

## Migration Guide

To migrate existing tests:

1. Identify common test patterns
2. Move test data to MetricsTestDataConstants
3. Extend AbstractMetricsProcessorTest
4. Implement abstract methods
5. Provide test cases via method sources
6. Remove redundant test methods
7. Keep specific tests that don't fit patterns

## Best Practices

1. **Use Constants**: Always use MetricsTestDataConstants for test data
2. **Descriptive Names**: Provide clear test case names in Arguments.of()
3. **Path Notation**: Use dot notation for nested JSON paths (e.g., "cpu.usage.avg")
4. **Validation Ranges**: Define reasonable min/max values for numeric validation
5. **Test Fixtures**: Create reusable fixtures for common scenarios
6. **Documentation**: Comment complex test cases and validation logic

## Example Test Output

When running parameterized tests, you'll see output like:
```
✓ shouldCreateExpectedOutputFile(String, TestFixture) ✓ Standard health benchmark
✓ shouldCreateExpectedOutputFile(String, TestFixture) ✓ Standard JWT validation benchmark
✓ shouldGenerateCorrectJsonStructure(String, TestFixture, String[]) ✓ Health endpoint structure
✓ shouldValidateNumericValues(String, TestFixture, NumericValidation) ✓ CPU usage percentage validation
```

This provides clear visibility into which test cases pass or fail.