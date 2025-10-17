# Claude Code Configuration

All AI development guidelines for this project are located in: **`doc/ai-rules.md`**

This file contains:
- Core process rules (critical)
- Task completion standards (mandatory)
- Code style guidelines
- Testing and logging standards
- Framework-specific standards
- AI tool specific instructions

Please refer to `doc/ai-rules.md` for complete guidance when working on this CUI JWT project.

## Custom Commands

### verifyCuiLoggingGuidelines

Verify that the codebase complies with CUI logging standards by:

1. **Analyze CUI logging standards** from `/Users/oliver/git/cui-llm-rules/standards/logging`
2. **Scan for logging violations** in the oauth-sheriff-core module:
   - Direct string usage in INFO/WARN/ERROR logging calls
   - Missing LogRecord definitions for structured messages
   - Incorrect parameter substitution patterns (should use '%s', not '{}' or '%d')
   - Wrong exception parameter ordering (exception should come first)
3. **Check LogRecord compliance**:
   - All INFO/WARN/ERROR logs must use LogRecord constants
   - Proper identifier ranges: INFO (001-099), WARN (100-199), ERROR (200-299)
   - DSL-Style Constants Pattern with static imports
4. **Validate documentation** in `doc/LogMessages.adoc` matches LogRecord definitions
5. **Run logging-related tests** to verify LogAsserts work with LogRecord format
6. **Generate compliance report** with:
   - Compliance percentage
   - List of violations found
   - Recommendations for fixes
   - Testing verification results

**Usage:** When user says "verifyCuiLoggingGuidelines", execute this comprehensive logging standards audit.

### fixOpenRewriteMarkers <module-path>

Fix all OpenRewrite TODO markers in a module following CUI standards:

**CRITICAL UNDERSTANDING**: Markers like `/*~~(TODO: INFO needs LogRecord)~~>*/` indicate **ACTUAL BUGS**, not just style issues.

**Execution Steps**:

1. **Locate All Markers**:
   ```bash
   grep -r "~~(TODO:" <module-path>/src --include="*.java"
   ```
   - Count markers: `grep -r "~~(TODO:" <module-path>/src --include="*.java" | wc -l`
   - Group by type: INFO needs LogRecord, WARN needs LogRecord, placeholder mismatches, etc.

2. **Analyze and Fix Each Marker**:

   **Production Code (src/main/java)**:
   - **Placeholder Mismatches**: Fix bugs like `LOGGER.info("value: %s", x, y)` (2 params, 1 placeholder)
     - Add missing `%s` placeholders or remove extra parameters
   - **Wrong Format Specifiers**: Change `%.2f`, `{:.2f}`, `{}`, `%d` → **ALWAYS use `%s`**
   - **Missing LogRecords**: Create LogRecord constants for INFO/WARN/ERROR messages
   - **Generic Exceptions**: Replace `Exception` with specific types (IOException, IllegalStateException)
   - **RuntimeException**: Replace with specific exception types (TokenValidationException, etc.)

   **Test Code (src/test/java)**:
   - **Diagnostic Logging** (performance tests, concurrency tests):
     - Add class-level suppression comment:
       ```java
       // cui-rewrite:disable CuiLogRecordPatternRecipe
       // This is a test/utility class that outputs diagnostic information for analysis
       ```
     - **NEVER create LogRecords for test diagnostic output**
   - **Placeholder Mismatches**: Fix bugs even in tests (change to %s)
   - **RuntimeException Throws**: Replace with AssertionError for test failures
   - **Generic Exception Catches**: Replace with specific types + InterruptedException/BrokenBarrierException

3. **Remove Markers After Fixing**:

   **macOS (BSD sed)**:
   ```bash
   find <module-path>/src -name "*.java" -exec sed -i '' 's|/\*~~(TODO: INFO needs LogRecord)~~>\*/||g; s|/\*~~(TODO: WARN needs LogRecord)~~>\*/||g; s|/\*~~(TODO: ERROR needs LogRecord)~~>\*/||g' {} +
   ```

   **Linux (GNU sed)**:
   ```bash
   find <module-path>/src -name "*.java" -exec sed -i 's|/\*~~(TODO: INFO needs LogRecord)~~>\*/||g; s|/\*~~(TODO: WARN needs LogRecord)~~>\*/||g; s|/\*~~(TODO: ERROR needs LogRecord)~~>\*/||g' {} +
   ```

   - Verify removal: `grep -r "~~(TODO:" <module-path>/src --include="*.java" | wc -l` (should be 0)

4. **Verify Fixes**:
   ```bash
   cd <module-path> && ../mvnw -Ppre-commit clean verify
   ```
   - All tests must pass
   - No compilation errors
   - Markers may reappear if bugs not truly fixed
   - If markers reappear, repeat steps 2-4

5. **Final Validation**:
   - Run full test suite: `cd <module-path> && ../mvnw clean install`
   - Confirm zero markers: `grep -r "~~(TODO:" <module-path>/src --include="*.java"`
   - Verify test count matches previous successful run (no tests accidentally broken)

**Common Bugs to Watch For**:
- `LOGGER.info("value %s", x, y)` → Missing placeholder for `y`
- `LOGGER.info("avg %.2f ms", avg)` → Use `%s` not `%.2f`
- `LOGGER.info("result {}", value)` → Use `%s` not `{}`
- `catch (Exception e)` → Use specific exception types
- `catch (RuntimeException e)` → Use IllegalArgumentException | IllegalStateException | TokenValidationException
- Test logging creating LogRecords → Use suppression comment instead

**Critical Rules**:
- ❌ **NEVER remove markers without fixing bugs**
- ❌ **NEVER create LogRecords for test diagnostic logging**
- ✅ **ALWAYS use `%s` for ALL string substitutions** (never `%.2f`, `{}`, `%d`)
- ✅ **ALWAYS fix placeholder/parameter count mismatches**
- ✅ **ALWAYS use specific exception types** (never Exception/RuntimeException)

**Usage:** When user says "fixOpenRewriteMarkers oauth-sheriff-core", execute this complete marker fixing workflow.

### verifyAndCommit <module-name>

Execute comprehensive quality verification and commit workflow for a specific module:

1. **Quality Verification Build** (pre-commit profile):
   ```bash
   ./mvnw -Ppre-commit clean verify -pl <module-name>
   ```
   - Runs code quality checks (checkstyle, spotbugs, PMD)
   - Performs static analysis
   - Validates code formatting and style compliance
   - **NO SHORTCUTS** - Fix ALL errors and warnings before proceeding

2. **Final Verification Build** (full integration):
   ```bash
   ./mvnw clean install -pl <module-name>
   ```
   - Runs complete build with all tests
   - Validates full integration and functionality
   - Ensures no regressions introduced
   - This will take nearly 8 Minutes. Always wait for it to complete. !0 minutes on the outside
   - **NO SHORTCUTS** - Fix ALL test failures and build errors

3. **Error Resolution Loop**:
   - If ANY errors or warnings occur in either build, STOP and fix them
   - Re-run the failed build command until it passes completely
   - DO NOT proceed to next step until current step is 100% clean
   - Apply fixes systematically and verify each fix

4. **Artifact Cleanup Verification**:
   ```bash
   find <module-name>/src/main/java -name "*.class" -type f
   find <module-name>/src/test/java -name "*.class" -type f
   find <module-name>/src -name "*.jar" -type f
   find <module-name>/src -name "*.war" -type f
   find <module-name>/src -name "target" -type d
   ```
   - Verify NO class files exist in source directories
   - Verify NO jar/war files exist in source directories
   - Verify NO target directories exist in source directories
   - Ensure NO build artifacts contaminate source code
   - Clean up any artifacts found before proceeding
   - **FAIL BUILD** if any artifacts are found in src/ directories

5. **Git Commit**:
   - Only proceed to commit when ALL steps pass completely
   - Create descriptive commit message explaining the changes
   - Include Co-Authored-By: Claude footer

**Usage:** When user says "verifyAndCommit oauth-sheriff-core", execute this complete verification and commit workflow for the oauth-sheriff-core module.

**Critical Rules:**
- **NEVER skip error fixes** - Every warning and error must be resolved
- **NEVER use shortcuts** - Run complete verification cycles
- **NEVER commit with failing builds** - Only commit when everything passes
- **NEVER commit with source artifacts** - Source directories must be clean of .class files
- **ALWAYS fix issues systematically** - Address root causes, not symptoms

## Slash Commands

The project includes custom slash commands located in `.claude/commands/` (tracked in git, shared across the team):

### /verify-project [push]

Comprehensive project verification workflow that runs the full Maven build with pre-commit profile.

- Reads execution duration from `doc/commands.md`
- Runs: `./mvnw -Ppre-commit clean install`
- Analyzes all errors, warnings, and OpenRewrite markers
- Fixes issues and repeats until clean
- Updates execution duration if changed >10%
- Optional `push` parameter: automatically commits and pushes changes after successful verification

**Usage:** `/verify-project` or `/verify-project push`

### /verify-integration-tests

Integration tests verification with comprehensive Quarkus/Keycloak log analysis.

- Reads execution duration from `doc/commands.md`
- Runs: `./mvnw clean verify -Pintegration-tests -pl oauth-sheriff-quarkus-parent/oauth-sheriff-quarkus-integration-tests`
- Thoroughly analyzes Maven output AND all Quarkus/Keycloak logs in target directory
- Checks warnings against acceptable list in `doc/commands.md`
- **ASKS USER** before adding warnings to acceptable list
- Fixes issues in any module and repeats until clean
- Runs `/verify-project` if code changes were made
- Updates execution duration if changed >10%

**Usage:** `/verify-integration-tests`

### /verify-micro-benchmark

Micro-benchmark verification with comprehensive JMH results analysis.

- Reads execution duration from `doc/commands.md`
- Runs: `./mvnw clean verify -pl benchmarking/benchmark-core -Pbenchmark`
- Analyzes Maven output for errors and warnings
- **VALIDATES benchmark results** in `benchmarking/benchmark-core/target/benchmark-results`
- Checks that benchmark files exist and contain valid results
- Verifies all benchmarks completed successfully with no errors
- **ASKS USER** before adding warnings to acceptable list
- Fixes issues in any module and repeats until clean
- Runs `/verify-project` if code changes were made
- Updates execution duration if changed >10%

**Usage:** `/verify-micro-benchmark`

### /verify-integration-benchmark

WRK-based integration benchmark verification with comprehensive result and log analysis.

- Reads execution duration from `doc/commands.md`
- Runs: `./mvnw clean verify -Pbenchmark -pl benchmarking/benchmark-integration-wrk`
- Analyzes Maven output for errors and warnings
- **VALIDATES benchmark results** in `benchmarking/benchmark-integration-wrk/target/benchmark-results`:
  - `wrk-health-results.json` - Health endpoint performance
  - `wrk-jwt-results.json` - JWT validation performance
  - Raw WRK output files
- **ANALYZES server logs** from `quarkus-logs.txt` for errors and warnings
- Verifies reasonable performance numbers (req/s, latency)
- Checks error counts (should be 0)
- **ASKS USER** before adding warnings to acceptable list
- Fixes issues in any module and repeats until clean
- Runs `/verify-project` if code changes were made
- Updates execution duration if changed >10%

**Usage:** `/verify-integration-benchmark`

### /verify-all [push]

Comprehensive end-to-end verification that executes all verification commands in sequence.

- **Reads durations** from `doc/commands.md` and displays estimated time at start
- Executes in strict serial order (never parallel):
  1. `/verify-project`
  2. `/verify-integration-tests`
  3. `/verify-micro-benchmark`
  4. `/verify-integration-benchmark`
- Each command must complete 100% before the next starts
- Each command runs according to its exact definition (isolated execution)
- Stops immediately if any command fails
- Individual commands handle their own duration updates in `doc/commands.md`
- Optional `push` parameter: automatically commits and pushes changes after all verifications succeed
- Provides comprehensive summary report with:
  - Estimated vs actual duration comparison
  - Status of each command with individual durations
  - Issues found and fixed across all commands
  - Duration updates made by individual commands

**Expected Total Duration**: Read from `doc/commands.md` at start (~19 minutes based on current durations)

**Usage:** `/verify-all` or `/verify-all push`

**Note:** Slash command files are tracked in git under `.claude/commands/` and shared across the team. User-local settings are in `.claude/settings.local.json` (gitignored).