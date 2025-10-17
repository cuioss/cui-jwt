# Verify Micro Benchmark Command

Execute micro-benchmarks with comprehensive JMH results analysis and validation.

## WORKFLOW INSTRUCTIONS

### Step 1: Read Configuration
1. Check if `doc/commands.md` exists in the project root
2. If it doesn't exist, create it with initial structure
3. Read the `last-execution-duration` for the `./mvnw clean verify -pl benchmarking/benchmark-core -Pbenchmark` command
4. If no duration is recorded, use **300000ms (5 minutes)** as default (benchmarks typically take longer)
5. Read the list of "Acceptable Warnings" for this command from the same document

### Step 2: Execute Maven Build
1. Run from project root: `./mvnw clean verify -pl benchmarking/benchmark-core -Pbenchmark`
2. Calculate timeout: `last-execution-duration * 1.25` (25% safety margin to prevent premature timeouts)
3. Use the calculated timeout value (in milliseconds) for the Maven command
4. **DO NOT run in background** - wait for completion
5. Capture the complete output log
6. Record the actual execution time

### Step 3: Analyze Build Output
Thoroughly analyze the Maven output for:
- **Compilation errors** - MUST be fixed
- **Test failures** - MUST be fixed
- **JMH benchmark errors** - MUST be fixed
- **Warnings in console output** - Check against acceptable warnings

**WARNING HANDLING**:
1. For each warning or oddity found, check if it's listed in "Acceptable Warnings" in `doc/commands.md`
2. If NOT in acceptable warnings list:
   - **STOP and ASK USER** whether this warning is acceptable
   - **WAIT for user response** before continuing
   - If user says it's acceptable, add it to "Acceptable Warnings" in `doc/commands.md`
   - If user says it needs fixing, proceed to Step 4
3. Only continue to Step 4 if there are issues that need fixing

### Step 4: Validate Benchmark Results
**CRITICAL BENCHMARK RESULTS VALIDATION**:
1. Check that `benchmarking/benchmark-core/target/benchmark-results` directory exists
2. List all files in the benchmark-results directory
3. Validate the following **required benchmark output files**:
   - **`micro-result.json`** - JMH benchmark results in JSON format - MUST exist and contain valid benchmark data
   - **`jwt-validation-metrics.json`** - Library-specific performance metrics - MUST exist and contain valid JSON
   - **`gh-pages-ready/data/benchmark-data.json`** - Processed benchmark data for documentation/visualization - MUST exist and contain valid JSON with metadata, overview, benchmarks array, and chartData
4. Analyze benchmark results for:
   - **All benchmarks completed successfully** - No ERROR or FAILURE markers
   - **Performance metrics present** - Score, error margin, operations/second, etc.
   - **No anomalies** - Unusually low/high scores that might indicate problems
   - **Warm-up iterations completed** - JMH warm-up phase succeeded
   - **Measurement iterations completed** - JMH measurement phase succeeded
5. Read and validate `micro-result.json`:
   - Use `grep -E '"benchmark"|"primaryMetric"|"score"|"scoreError"'` to extract key metrics
   - Verify all expected benchmarks are present
   - Check that all scores are positive non-zero values
   - Ensure error margins are reasonable (not infinite or NaN)
6. Validate `jwt-validation-metrics.json`:
   - Ensure it contains performance breakdown by validation step
   - Check for metrics like: complete_validation, claims_validation, signature_validation, etc.
   - Verify all timing values (p50_us, p95_us, p99_us) are present and reasonable
7. Validate `gh-pages-ready/data/benchmark-data.json`:
   - Verify it contains required top-level keys: metadata, overview, benchmarks, chartData, trends
   - Check metadata has timestamp, displayTimestamp, benchmarkType, reportVersion
   - Verify overview section has throughput, latency, performanceScore, performanceGrade
   - Confirm benchmarks array contains all benchmark results with scores and percentiles
   - Ensure chartData has properly formatted data for visualization

**BENCHMARK RESULTS REQUIREMENTS**:
- `micro-result.json` must exist and contain valid JSON with JMH benchmark results
- `jwt-validation-metrics.json` must exist and contain valid JSON with library metrics
- `gh-pages-ready/data/benchmark-data.json` must exist and contain valid JSON with complete structure
- All benchmark files must be non-empty
- Results must show successful benchmark execution with valid performance numbers

### Step 5: Fix Issues
1. Fix all errors, failures, and warnings that need fixing
2. If fix is in a different module (not the benchmark-core module):
   - Rebuild that module first: `./mvnw clean install -pl <module-name>`
   - Then rebuild any dependent modules if needed
3. For each code change made, **REPEAT THE ENTIRE PROCESS** (go back to Step 2)
4. Continue until no more changes are needed

### Step 6: Post-Verification (Only if Code Changes Were Made)
If ANY code changes were made during the fixing process:
1. Run `/verify-project` to ensure the entire project still builds correctly
2. This ensures changes didn't break other modules

### Step 7: Update Duration and Report
Once the benchmarks complete successfully with no changes needed:
1. Calculate the percentage change: `|new_duration - old_duration| / old_duration * 100`
2. If the change is **greater than 10%**, update `last-execution-duration` in `doc/commands.md`
3. Display a summary report to the user:
   - Build status
   - Number of iterations performed
   - Issues found and fixed
   - Warnings handled
   - Benchmark results summary (number of benchmarks, location of results)
   - Execution time (and if it was updated)
   - Any items added to acceptable warnings
   - Whether /verify-project was run

## CRITICAL RULES

- **NEVER cancel the Maven build** - always wait for completion (benchmarks take time!)
- **ALWAYS use timeout = last-execution-duration * 1.25** (25% safety margin)
- **ALWAYS validate benchmark results** in `benchmarking/benchmark-core/target/benchmark-results`
- **ALWAYS check that benchmark files exist and are non-empty**
- **ALWAYS ask user** before adding new acceptable warnings
- **ALWAYS wait for user response** before continuing after asking
- **ALWAYS repeat** the process after making code changes
- **UPDATE duration only if change > 10%**
- **RUN /verify-project** if any code changes were made

## Example doc/commands.md Structure

```markdown
# Command Configuration

## ./mvnw clean verify -pl benchmarking/benchmark-core -Pbenchmark

### Last Execution Duration
- **Duration**: 300000ms (5 minutes)
- **Last Updated**: 2025-10-14

### Acceptable Warnings
- `[WARNING] JMH: Some benchmark warm-up iterations may be insufficient`
- `WARNING: A terminally deprecated method in sun.misc.Unsafe has been called`
```

## Usage

Simply invoke: `/verify-micro-benchmark`

No arguments needed. The command will automatically detect project root and execute the workflow.
