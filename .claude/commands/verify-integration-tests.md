# Verify Integration Tests Command

Execute integration tests with comprehensive Quarkus/Keycloak log analysis and issue tracking.

## WORKFLOW INSTRUCTIONS

### Step 1: Read Configuration
1. Check if `doc/commands.md` exists in the project root
2. If it doesn't exist, create it with initial structure
3. Read the `last-execution-duration` for the `./mvnw clean verify -Pintegration-tests -pl oauth-sheriff-quarkus-parent/oauth-sheriff-quarkus-integration-tests` command
4. If no duration is recorded, use **120000ms (2 minutes)** as default
5. Read the list of "Acceptable Warnings" for this command from the same document

### Step 2: Execute Maven Build
1. Run from project root: `./mvnw clean verify -Pintegration-tests -pl oauth-sheriff-quarkus-parent/oauth-sheriff-quarkus-integration-tests`
2. Calculate timeout: `last-execution-duration * 1.25` (25% safety margin to prevent premature timeouts)
3. Use the calculated timeout value (in milliseconds) for the Maven command
4. **DO NOT run in background** - wait for completion
5. Capture the complete output log
6. Record the actual execution time

### Step 3: Analyze Build Output
Thoroughly analyze the Maven output AND all Quarkus/Keycloak logs for:
- **Compilation errors** - MUST be fixed
- **Test failures** - MUST be fixed
- **Quarkus startup errors** - MUST be fixed
- **Keycloak errors** - MUST be investigated
- **Warnings in console output** - Check against acceptable warnings
- **Warnings in Quarkus logs** - Check against acceptable warnings
- **Warnings in Keycloak logs** - Check against acceptable warnings
- **Oddities in logs** - Unexpected behavior, strange timing, unusual messages

**CRITICAL LOG ANALYSIS**:
- Logs are copied to the `target` directory
- Read Quarkus application logs carefully
- Read Keycloak container logs carefully
- Look for:
  - Stack traces
  - ERROR level messages
  - WARN level messages
  - Connection issues
  - Timeout issues
  - Authentication/Authorization failures
  - Configuration problems

**WARNING HANDLING**:
1. For each warning or oddity found, check if it's listed in "Acceptable Warnings" in `doc/commands.md`
2. If NOT in acceptable warnings list:
   - **STOP and ASK USER** whether this warning is acceptable
   - **WAIT for user response** before continuing
   - If user says it's acceptable, add it to "Acceptable Warnings" in `doc/commands.md`
   - If user says it needs fixing, proceed to Step 4
3. Only continue to Step 4 if there are issues that need fixing

### Step 4: Fix Issues
1. Fix all errors, failures, and warnings that need fixing
2. If fix is in a different module (not the integration-tests module):
   - Rebuild that module first: `./mvnw clean install -pl <module-name>`
   - Then rebuild any dependent modules if needed
3. For each code change made, **REPEAT THE ENTIRE PROCESS** (go back to Step 2)
4. Continue until no more changes are needed

### Step 5: Post-Verification (Only if Code Changes Were Made)
If ANY code changes were made during the fixing process:
1. Run `/verify-project` to ensure the entire project still builds correctly
2. This ensures changes didn't break other modules

### Step 6: Update Duration and Report
Once the integration tests complete successfully with no changes needed:
1. Calculate the percentage change: `|new_duration - old_duration| / old_duration * 100`
2. If the change is **greater than 10%**, update `last-execution-duration` in `doc/commands.md`
3. Display a summary report to the user:
   - Build status
   - Number of iterations performed
   - Issues found and fixed
   - Warnings handled
   - Execution time (and if it was updated)
   - Any items added to acceptable warnings
   - Whether /verify-project was run

## CRITICAL RULES

- **NEVER cancel the Maven build** - always wait for completion
- **ALWAYS use timeout = last-execution-duration * 1.25** (25% safety margin)
- **ALWAYS read and analyze Quarkus/Keycloak logs** from target directory
- **ALWAYS ask user** before adding new acceptable warnings
- **ALWAYS wait for user response** before continuing after asking
- **ALWAYS repeat** the process after making code changes
- **UPDATE duration only if change > 10%**
- **RUN /verify-project** if any code changes were made

## Example doc/commands.md Structure

```markdown
# Command Configuration

## ./mvnw clean verify -Pintegration-tests -pl oauth-sheriff-quarkus-parent/oauth-sheriff-quarkus-integration-tests

### Last Execution Duration
- **Duration**: 120000ms (2 minutes)
- **Last Updated**: 2025-10-14

### Acceptable Warnings
- `[WARNING] Keycloak startup: Using temporary development settings`
- `[WARNING] Quarkus: Unrecognized configuration key in application.properties`
```

## Usage

Simply invoke: `/verify-integration-tests`

No arguments needed. The command will automatically detect project root and execute the workflow.
