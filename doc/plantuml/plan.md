# PlantUML Theme Variants Plan

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

## Current State

### Existing Diagrams (5 files)
1. `component-overview.puml` - High-level architecture with package-level view
2. `key-management.puml` - Simplified sequence diagram showing key rotation
3. `multi-issuer-support.puml` - Sequence diagram for multi-issuer resolution
4. `token-structure.puml` - Standard JWT structure diagram
5. `token-types.puml` - Class diagram showing token type hierarchy

### Current Styling
- Using PlantUML defaults (no custom skin)
- Clean and professional but generic
- No visual appeal or branding

---

## Goal: Create Apple BigSur-Style Elegant Themes

**Design Philosophy:**
- **Elegant, not fancy** - Clean, minimal, sophisticated
- **Apple BigSur aesthetic** - Light, airy, subtle depth
- **Readability first** - Text must remain perfectly legible
- **Consistent** - All diagrams work well with the theme

**Visual Characteristics to Target:**
- Light backgrounds (white or very subtle gray)
- Thin borders (1-2px max)
- Subtle rounded corners
- Proper spacing and padding
- System fonts (SF Pro or similar fallback)
- Minimal shadows (if any)
- Subtle color accents (blues, grays)

---

## Theme Variant Creation Plan

### Phase 1: Research and Baseline

**Task 1.1: Create baseline directory**
- Copy current PNGs to `theme-baseline/` for comparison
- Document current state as "Default PlantUML"

**Task 1.2: Research PlantUML skinparam**
- Study skinparam documentation for:
  - Component diagrams (rectangles, packages, arrows)
  - Sequence diagrams (actors, lifelines, boxes, notes)
  - Class diagrams (classes, interfaces, relationships)
- Identify key parameters for Apple-style aesthetics

**Task 1.3: Research Apple BigSur design language**
- Color palettes (System Blue #007AFF, grays)
- Typography (SF Pro, Helvetica Neue fallbacks)
- Border styles and corner radius
- Spacing and padding conventions

---

### Phase 2: Create Theme Variants (3-5 options)

**For EACH variant, create:**
1. Skin file: `theme-proposals/variant-N.skin`
2. Build ALL 5 diagrams with the variant
3. Save PNGs to `theme-proposals/variant-N/`
4. Document visual characteristics
5. Rate readability and elegance

**Variant 1: "Minimal Light"**
- Pure white backgrounds
- Very thin borders (1px)
- No shadows
- Subtle rounded corners (2-4px)
- System Blue accents
- Focus: Maximum minimalism

**Variant 2: "Subtle Depth"**
- Off-white backgrounds (#FAFAFA)
- Thin borders with slight gray (#E5E5E5)
- Very subtle drop shadows
- Rounded corners (4px)
- Soft blue accents (#5AC8FA)
- Focus: Gentle depth without clutter

**Variant 3: "Clean Professional"**
- White backgrounds
- Medium borders (1.5px) in dark gray
- No shadows
- Moderate rounded corners (6px)
- Professional blue (#0071E3)
- Focus: Corporate polish

**Variant 4: "Airy Modern" (Optional)
- Very light gray backgrounds (#F5F5F7)
- Hairline borders (0.5px)
- No shadows
- Generous padding
- Light blue accents (#007AFF at 80% opacity)
- Focus: Spacious, breathable

**Variant 5: "Refined Contrast" (Optional)
- White backgrounds
- Slightly thicker borders (2px) for clarity
- Subtle inner borders
- Rounded corners (5px)
- Rich blue (#0077ED)
- Focus: Clarity through controlled contrast

---

### Phase 3: Evaluation and Selection

**Task 3.1: Visual comparison**
- Create comparison grid showing same diagram across all variants
- Evaluate each variant on:
  - **Readability** (1-10): Can all text be read easily?
  - **Elegance** (1-10): Does it look Apple-inspired and refined?
  - **Consistency** (1-10): Do all 5 diagrams work well?
  - **Distinctiveness** (1-10): Does it stand out from default?

**Task 3.2: Select final theme**
- Choose the variant with best overall scores
- Prioritize readability > elegance > distinctiveness
- Get user approval on selection

**Task 3.3: Apply final theme**
- Copy selected skin to `plantuml.skin`
- Add `!include plantuml.skin` to all 5 diagrams
- Build and verify all diagrams
- Clean up `theme-proposals/` directory

---

### Phase 4: Final Integration

**Task 4.1: Documentation**
- Document chosen theme characteristics
- Explain visual improvements
- Note any tradeoffs made

**Task 4.2: Cleanup**
- Remove `theme-baseline/` directory
- Remove `theme-proposals/` directory
- Keep only final `plantuml.skin` and diagrams

**Task 4.3: Commit**
- Commit final theme with all updated diagrams
- Include before/after comparison in commit message

---

## Execution Strategy

1. **DO NOT start implementing until this plan is reviewed and committed**
2. **Create ALL variants BEFORE evaluation** - don't optimize early
3. **Test each variant on ALL 5 diagrams** - no shortcuts
4. **Prioritize readability** - if text becomes hard to read, reject the variant
5. **Keep backups** - baseline directory must be preserved until final commit

---

## Success Criteria

✅ All 5 diagrams work perfectly with chosen theme
✅ Text remains perfectly legible (no smaller fonts, no low contrast)
✅ Visual style is noticeably more elegant than defaults
✅ Apple BigSur aesthetic is achieved
✅ Theme is maintainable (simple skin file, not overly complex)
✅ No rendering errors or visual glitches

---

## Notes

- **Elegant ≠ Fancy**: We want sophistication through simplicity, not decoration
- **Readability is non-negotiable**: If a theme hurts readability, it fails
- **Consistency matters**: A theme that works for 4/5 diagrams is not good enough
- **Test thoroughly**: Build and visually inspect EVERY diagram EVERY time
