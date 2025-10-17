# Verify All Command

Execute comprehensive verification of the entire project by running all verification commands in sequence.

## WORKFLOW INSTRUCTIONS

This command executes the following verification commands in **strict serial order**:

1. `/verify-project`
2. `/verify-integration-tests`
3. `/verify-micro-benchmark`
4. `/verify-integration-benchmark`

### Step 0: Read Configuration and Display Estimated Duration

1. Check if `doc/commands.md` exists in the project root
2. If it doesn't exist, create it with initial structure for all commands
3. Read the `last-execution-duration` for all four commands:
   - `./mvnw -Ppre-commit clean install` (verify-project)
   - `./mvnw clean verify -Pintegration-tests -pl oauth-sheriff-quarkus-parent/oauth-sheriff-quarkus-integration-tests` (verify-integration-tests)
   - `./mvnw clean verify -pl benchmarking/benchmark-core -Pbenchmark` (verify-micro-benchmark)
   - `./mvnw clean verify -Pbenchmark -pl benchmarking/benchmark-integration-wrk` (verify-integration-benchmark)
4. If any duration is not recorded, use these defaults:
   - verify-project: **385000ms (6 minutes 25 seconds)**
   - verify-integration-tests: **160000ms (2 minutes 40 seconds)**
   - verify-micro-benchmark: **260000ms (4 minutes 20 seconds)**
   - verify-integration-benchmark: **347000ms (5 minutes 47 seconds)**
5. Calculate total estimated duration by summing all four durations
6. Display to user:
   ```
   📊 Estimated Execution Times (from doc/commands.md):
   1. /verify-project: X minutes Y seconds
   2. /verify-integration-tests: X minutes Y seconds
   3. /verify-micro-benchmark: X minutes Y seconds
   4. /verify-integration-benchmark: X minutes Y seconds

   Total Estimated Duration: ~X minutes Y seconds

   Starting comprehensive verification workflow...
   ```

### Critical Rules

- **SERIAL EXECUTION ONLY** - Execute commands one at a time, never in parallel
- **WAIT FOR COMPLETION** - Each command must complete 100% before starting the next
- **ISOLATED EXECUTION** - Each command runs independently with its own complete workflow
- **NO SHORTCUTS** - Each command must execute according to its exact definition
- **STOP ON FAILURE** - If any command fails, stop and report the failure
- **NO COMMAND MODIFICATION** - Do not read or modify `doc/commands.md` (individual commands handle that)

### Execution Steps

#### Step 1: Execute /verify-project

1. Use the SlashCommand tool to execute `/verify-project`
2. Wait for the command to complete entirely
3. Verify the command completed successfully
4. If the command failed, STOP and report failure
5. If successful, proceed to Step 2

#### Step 2: Execute /verify-integration-tests

1. Use the SlashCommand tool to execute `/verify-integration-tests`
2. Wait for the command to complete entirely
3. Verify the command completed successfully
4. If the command failed, STOP and report failure
5. If successful, proceed to Step 3

#### Step 3: Execute /verify-micro-benchmark

1. Use the SlashCommand tool to execute `/verify-micro-benchmark`
2. Wait for the command to complete entirely
3. Verify the command completed successfully
4. If the command failed, STOP and report failure
5. If successful, proceed to Step 4

#### Step 4: Execute /verify-integration-benchmark

1. Use the SlashCommand tool to execute `/verify-integration-benchmark`
2. Wait for the command to complete entirely
3. Verify the command completed successfully
4. If the command failed, STOP and report failure
5. If successful, proceed to Step 5

#### Step 5: Optional Push (if push parameter provided)

If the user invoked the command with the `push` parameter (e.g., `/verify-all push`):

1. Create a git commit with message summarizing all verification results
2. Push the changes to the remote repository using `git push`
3. Include Co-Authored-By: Claude footer

If no `push` parameter was provided, skip this step.

#### Step 6: Final Report

1. Record the actual total execution time across all commands
2. Compare actual vs estimated duration
3. Display a comprehensive summary report including:
   - **Estimated Duration**: Total from Step 0
   - **Actual Duration**: Total measured time
   - **Status of each verification command**: (SUCCESS/FAILURE) with individual durations
     - `/verify-project`: SUCCESS/FAILURE (X min Y sec)
     - `/verify-integration-tests`: SUCCESS/FAILURE (X min Y sec)
     - `/verify-micro-benchmark`: SUCCESS/FAILURE (X min Y sec)
     - `/verify-integration-benchmark`: SUCCESS/FAILURE (X min Y sec)
   - **Total number of issues found and fixed** across all commands
   - **Summary of any warnings handled** by individual commands
   - **Duration updates** made by individual commands to `doc/commands.md`
   - **Whether changes were pushed** (if push parameter was used)
   - **Accuracy**: Compare estimated vs actual (percentage difference)

### Example Invocation

Without push:
```
/verify-all
```

With automatic push:
```
/verify-all push
```

### Important Notes

- **Each command is self-contained** - They handle their own error fixing, retries, and duration updates
- **This command is an orchestrator** - It only executes commands and reports results
- **Total execution time** - Read from `doc/commands.md` at start (currently ~19 minutes based on latest durations)
- **Duration display** - Shows estimated time at start based on historical data from `doc/commands.md`
- **Duration tracking** - Individual commands update their durations in `doc/commands.md` if changed >10%
- **Isolation** - Each command starts fresh and doesn't carry state from previous commands
- **Comprehensive validation** - This ensures the entire project is verified end-to-end

### Parameter Handling

The command accepts one optional parameter:
- `push` - If provided, automatically commit and push changes after all verifications succeed

Extract the parameter from the command invocation. Examples:
- `/verify-all` → No push
- `/verify-all push` → Auto-push enabled
