# PlantUML Diagram Refactoring Plan

## CRITICAL: Verification Process (MUST BE FOLLOWED AFTER EVERY CHANGE)

**Rule: After EVERY change, you MUST:**

1. **Build ALL diagrams:**
   ```bash
   cd /Users/oliver/git/OAuth-Sheriff/doc/plantuml
   for file in *.puml; do plantuml "$file"; done
   ```

2. **Visually verify EVERY PNG:**
   - Read each PNG file using the Read tool
   - Check that components/boxes are visible and properly styled
   - Verify text is readable
   - Confirm layout is correct
   - Ensure no black backgrounds or rendering errors

3. **Document results:**
   - Screenshot/describe what changed visually
   - Note any issues or improvements
   - Update this plan.md with verification status

**If ANY diagram is broken or looks worse, STOP and fix it before continuing.**

---

## Current State Analysis

### Existing Diagrams
1. `component-overview.puml` - 26 components, component diagram
2. `key-management.puml` - 120 lines, complex sequence diagram
3. `multi-issuer-support.puml` - Component diagram with multiple issuers
4. `threat-model-dataflow.puml` - Security dataflow diagram
5. `token-structure.puml` - Standard JWT structure
6. `token-types.puml` - Class diagram showing token types

### Existing Skin File
- `plantuml.skin` - 98 lines
- Uses custom colors: #ECF0F1 (gray bg), #2C3E50 (blue-gray borders)
- Uses `!pragma layout smetana`
- Not elegant, not Apple-style
- **Status: To be removed**

---

## Tasks

### Task 1: Remove Existing Skin
**Sub-tasks:**
- [ ] 1.1: Delete `plantuml.skin` file
- [ ] 1.2: Remove all `!include plantuml.skin` lines from all `.puml` files
- [ ] 1.3: **BUILD & VERIFY** all diagrams render with PlantUML defaults
- [ ] 1.4: Document how diagrams look with default styling
- [ ] 1.5: **COMMIT** skin removal

### Task 2: Create Minimal Elegant Skin
**Goal:** Create a clean, Apple-inspired theme with proper testing

**Sub-tasks:**
- [ ] 2.1: Research PlantUML skinparam documentation
- [ ] 2.2: Create `plantuml-minimal.skin` with ONLY essential settings:
  - White/near-white backgrounds
  - Thin, clean borders
  - Proper font sizes
  - No shadows
  - Subtle rounded corners
- [ ] 2.3: Test skin on ONE diagram first (token-types.puml)
- [ ] 2.4: **BUILD & VERIFY** that test diagram looks good
- [ ] 2.5: If test passes, apply to all diagrams
- [ ] 2.6: **BUILD & VERIFY** all diagrams
- [ ] 2.7: Document visual improvements
- [ ] 2.8: **COMMIT** new minimal elegant skin

### Task 3: Simplify key-management.puml
**Goal:** Reduce from 120 lines to ~40-50 lines, focus on essential flow

**Current issues:**
- Too detailed (3 sections: Initialization, Key Retrieval, Rotation)
- Too many participants (12)
- Complex notes at bottom

**Sub-tasks:**
- [ ] 3.1: Analyze current diagram, identify essential vs. detail
- [ ] 3.2: Create simplified version showing only:
  - Main initialization flow
  - Key rotation concept
  - Essential participants only
- [ ] 3.3: Move detailed notes to separate documentation file
- [ ] 3.4: **BUILD & VERIFY** simplified diagram is clear and accurate
- [ ] 3.5: Document what was removed and why
- [ ] 3.6: **COMMIT** simplified key-management diagram

### Task 4: Audit component-overview.puml
**Goal:** Ensure diagram uses standard PlantUML component syntax

**Sub-tasks:**
- [ ] 4.1: Review diagram for non-standard syntax
- [ ] 4.2: Ensure all components use standard `component` keyword
- [ ] 4.3: Verify relationships use standard arrows
- [ ] 4.4: **BUILD & VERIFY** diagram renders correctly
- [ ] 4.5: Document any changes made
- [ ] 4.6: **COMMIT** component-overview audit results

### Task 5: Audit multi-issuer-support.puml
**Goal:** Ensure diagram uses standard PlantUML component syntax

**Sub-tasks:**
- [ ] 5.1: Review diagram for non-standard syntax
- [ ] 5.2: Simplify if overly complex
- [ ] 5.3: **BUILD & VERIFY** diagram renders correctly
- [ ] 5.4: Document any changes made
- [ ] 5.5: **COMMIT** multi-issuer-support audit results

### Task 6: Audit threat-model-dataflow.puml
**Goal:** Ensure diagram uses standard syntax, verify threat colors work without skin

**Sub-tasks:**
- [ ] 6.1: Review diagram for custom color definitions
- [ ] 6.2: Replace any `$THREAT_COLOR` etc. with inline colors or remove
- [ ] 6.3: **BUILD & VERIFY** diagram renders correctly
- [ ] 6.4: Document any changes made
- [ ] 6.5: **COMMIT** threat-model-dataflow audit results

### Task 7: Audit token-structure.puml
**Goal:** Verify standard JWT diagram needs no changes

**Sub-tasks:**
- [ ] 7.1: Review diagram
- [ ] 7.2: **BUILD & VERIFY** diagram renders correctly
- [ ] 7.3: Document status (likely no changes needed)
- [ ] 7.4: **COMMIT** token-structure audit results (if any changes)

### Task 8: Audit token-types.puml
**Goal:** Verify class diagram uses standard syntax

**Sub-tasks:**
- [ ] 8.1: Review diagram for non-standard syntax
- [ ] 8.2: **BUILD & VERIFY** diagram renders correctly
- [ ] 8.3: Document any changes made
- [ ] 8.4: **COMMIT** token-types audit results (if any changes)

### Task 9: Final Integration
**Sub-tasks:**
- [ ] 9.1: **BUILD & VERIFY** all 6 diagrams one final time
- [ ] 9.2: Compare with baseline PNGs from Task 0
- [ ] 9.3: Document all improvements
- [ ] 9.4: Create summary report of changes
- [ ] 9.5: Clean up `theme-proposals/` directory (no longer needed)
- [ ] 9.6: Clean up `baseline/` directory if satisfied
- [ ] 9.7: Commit all changes

---

## Notes

- Keep each task small and focused
- Never skip the BUILD & VERIFY step
- If a diagram breaks, rollback immediately and investigate
- Prioritize visual quality over speed
- Elegant = simple, clean, not fancy

---

## Progress Log

### [Date] - [Task] - [Status]
(To be filled in as work progresses)
