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
2. **Scan for logging violations** in the cui-jwt-validation module:
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

**Usage:** When user says "verifyAndCommit cui-jwt-validation", execute this complete verification and commit workflow for the cui-jwt-validation module.

**Critical Rules:**
- **NEVER skip error fixes** - Every warning and error must be resolved
- **NEVER use shortcuts** - Run complete verification cycles
- **NEVER commit with failing builds** - Only commit when everything passes
- **NEVER commit with source artifacts** - Source directories must be clean of .class files
- **ALWAYS fix issues systematically** - Address root causes, not symptoms