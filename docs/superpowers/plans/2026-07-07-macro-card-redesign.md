# Macro Card Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make macro cards fill the full screen width (one per row instead of two) and add an inline on/off switch on every card, wired to the existing (already-correct) enable/disable machinery.

**Architecture:** `MacroListScreen.kt`'s grid drops from 2 columns to 1. `MacroCard.kt` gains a `ThemedSwitch` (the capsule toggle already used in the editor) placed in the trailing control cluster — after the status dot/AI chip, before the drag handle — wired straight through to `MacroListViewModel.onToggle`, which already calls the correct `ToggleMacroUseCase` (alarm cancel/re-arm, geofence register/remove). The whole card also dims when disabled, extending the existing accent-bar dim treatment.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), existing `sh.calvin.reorderable` grid-reorder state — no new dependencies, no DB/schema change.

## Global Constraints

- Project root: `/Users/larssohl/Documents/Claude Work/Automatiq` (this is the current location — the old `~/VibeActions` path referenced in older plans in this repo no longer exists).
- No DB migration, no new Gradle dependencies, no changes to `Macro`/`MacroEntity` — `enabled` already exists on both.
- Do not touch the Danish-text strings noticed during design (dropdown menu items, editor/settings labels, Gemini prompt language) — that is explicitly out of scope, tracked in the spec's Follow-ups section.
- Do not change card height (stays 76dp), do not wrap titles to multiple lines, do not change the drag-reorder gesture or the long-press actions menu.
- No Compose UI test harness exists in this project (`androidTest` is empty) — verification is `./gradlew assembleDebug` / `testDebugUnitTest` for build correctness, plus explicit manual on-device steps, matching this repo's established pattern for UI-only work.
- **Pre-flight:** `app/src/main/java/com/vibeactions/ui/common/ThemedSwitch.kt` currently has an *uncommitted* restyle on the `feat/themed-switch` branch, and this plan's Task 2 depends on that restyled component. If execution uses an isolated git worktree (via `superpowers:using-git-worktrees`), **commit that pending `ThemedSwitch.kt` change first** — an uncommitted change in the main working copy will not exist in a fresh worktree checked out from a branch/commit.

---

## File Map

| Action | File |
|--------|------|
| Modify | `app/src/main/java/com/vibeactions/ui/macrolist/MacroListScreen.kt` |
| Modify | `app/src/main/java/com/vibeactions/ui/common/MacroCard.kt` |

---

## Task 1: Single-column full-width list

Change the macro list from a 2-column grid to 1 column, so every card fills the screen width.

**Files:**
- Modify: `app/src/main/java/com/vibeactions/ui/macrolist/MacroListScreen.kt:80`

**Interfaces:**
- Consumes: nothing new (uses existing `LazyVerticalGrid`, `GridCells`, `rememberReorderableLazyGridState`).
- Produces: nothing new — pure layout change, no signature changes.

- [ ] **Step 1: Change the grid to a single column**

In `MacroListScreen.kt`, find:

```kotlin
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
```

Change to:

```kotlin
                LazyVerticalGrid(
                    columns = GridCells.Fixed(1),
```

No other line in that block changes — `state`, `verticalArrangement`, `horizontalArrangement`, and `contentPadding` all carry over as-is.

- [ ] **Step 2: Build**

```bash
cd "/Users/larssohl/Documents/Claude Work/Automatiq" && ./gradlew assembleDebug 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
cd "/Users/larssohl/Documents/Claude Work/Automatiq"
git add app/src/main/java/com/vibeactions/ui/macrolist/MacroListScreen.kt
git commit -m "feat: macro list — one full-width card per row instead of a 2-column grid"
```

---

## Task 2: Inline on/off switch on every card

Add a `ThemedSwitch` to `MacroCard` wired to the existing toggle plumbing, so a macro can be enabled/disabled without opening the editor.

**Files:**
- Modify: `app/src/main/java/com/vibeactions/ui/common/MacroCard.kt:36-44` (signature), `:88-108` (insert point), `:109-120` (existing drag handle, for placement reference)
- Modify: `app/src/main/java/com/vibeactions/ui/macrolist/MacroListScreen.kt:88-97` (call site)

**Interfaces:**
- Consumes:
  - `MacroListViewModel.onToggle(macro: Macro, enabled: Boolean)` — already exists, unmodified (`app/src/main/java/com/vibeactions/ui/macrolist/MacroListViewModel.kt:31`).
  - `ThemedSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true)` — already exists in the same package, unmodified.
  - `Macro.enabled: Boolean` — already exists.
- Produces: `MacroCard(..., onToggle: (Boolean) -> Unit, ...)` — new required parameter. (No other file in this plan calls `MacroCard` besides the one call site updated in this same task.)

- [ ] **Step 1: Add the `onToggle` parameter to `MacroCard`**

In `MacroCard.kt`, change the function signature from:

```kotlin
@Composable
fun MacroCard(
    macro: Macro,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier
) {
```

to:

```kotlin
@Composable
fun MacroCard(
    macro: Macro,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onSend: () -> Unit,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier
) {
```

- [ ] **Step 2: Insert the switch between the status column and the drag handle**

In `MacroCard.kt`, the row currently goes straight from the status `Column` (status dot + optional "AI" chip) to the drag-handle `Box`:

```kotlin
        Column(
            Modifier
                .fillMaxHeight()
                .padding(vertical = 6.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.End
        ) {
            StatusDot(macro.lastStatus)
            if (macro.triggerType == TriggerType.INCOMING && macro.aiReplyEnabled) {
                Spacer(Modifier.height(4.dp))
                Surface(color = accent.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                    Text(
                        "AI",
                        color = accent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
        }
        // Drag handle for reordering (long-press is now reserved for the actions menu).
        Box(
            Modifier.fillMaxHeight().padding(end = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.DragHandle,
                contentDescription = "Reorder",
                tint = OnSurfaceVariant,
                modifier = dragHandleModifier.padding(6.dp)
            )
        }
```

Insert a new `Box` for the switch between them (same `fillMaxHeight` + centered pattern as the drag-handle box, so it lines up vertically with its siblings):

```kotlin
        Column(
            Modifier
                .fillMaxHeight()
                .padding(vertical = 6.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.End
        ) {
            StatusDot(macro.lastStatus)
            if (macro.triggerType == TriggerType.INCOMING && macro.aiReplyEnabled) {
                Spacer(Modifier.height(4.dp))
                Surface(color = accent.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                    Text(
                        "AI",
                        color = accent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Box(
            Modifier.fillMaxHeight().padding(end = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            ThemedSwitch(
                checked = macro.enabled,
                onCheckedChange = onToggle
            )
        }
        // Drag handle for reordering (long-press is now reserved for the actions menu).
        Box(
            Modifier.fillMaxHeight().padding(end = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.DragHandle,
                contentDescription = "Reorder",
                tint = OnSurfaceVariant,
                modifier = dragHandleModifier.padding(6.dp)
            )
        }
```

`ThemedSwitch` needs no import — it's declared in the same file, same `com.vibeactions.ui.common` package.

- [ ] **Step 3: Wire the call site in `MacroListScreen.kt`**

In `MacroListScreen.kt`, change:

```kotlin
                            MacroCard(
                                macro = macro,
                                onClick = { editorMacroId = macro.id; showEditor = true },
                                onDelete = { deleteMacro(macro) },
                                onCopy = { vm.onCopy(macro) },
                                onSend = { sendMacro(macro) },
                                dragHandleModifier = Modifier.draggableHandle(
                                    onDragStopped = { vm.onReorder(ordered.map(Macro::id)) }
                                )
                            )
```

to:

```kotlin
                            MacroCard(
                                macro = macro,
                                onClick = { editorMacroId = macro.id; showEditor = true },
                                onDelete = { deleteMacro(macro) },
                                onCopy = { vm.onCopy(macro) },
                                onSend = { sendMacro(macro) },
                                onToggle = { enabled -> vm.onToggle(macro, enabled) },
                                dragHandleModifier = Modifier.draggableHandle(
                                    onDragStopped = { vm.onReorder(ordered.map(Macro::id)) }
                                )
                            )
```

- [ ] **Step 4: Build**

```bash
cd "/Users/larssohl/Documents/Claude Work/Automatiq" && ./gradlew assembleDebug 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
cd "/Users/larssohl/Documents/Claude Work/Automatiq"
git add app/src/main/java/com/vibeactions/ui/common/MacroCard.kt app/src/main/java/com/vibeactions/ui/macrolist/MacroListScreen.kt
git commit -m "feat: MacroCard — inline on/off switch wired to existing ToggleMacroUseCase"
```

---

## Task 3: Dim the whole card when disabled

Extend the existing accent-bar dim (`if (macro.enabled) accent else Outline`) to the whole card, so a disabled macro reads as disabled at a glance, not just via the bar color.

**Files:**
- Modify: `app/src/main/java/com/vibeactions/ui/common/MacroCard.kt` (imports, and the outer `Row`'s modifier chain)

**Interfaces:**
- Consumes: `Macro.enabled: Boolean` — already exists.
- Produces: nothing new exposed.

- [ ] **Step 1: Add the `alpha` import**

In `MacroCard.kt`, add this import alongside the existing `androidx.compose.ui.draw.clip` import:

```kotlin
import androidx.compose.ui.draw.alpha
```

- [ ] **Step 2: Apply alpha to the outer Row when disabled**

Change:

```kotlin
    Row(
        modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface.copy(alpha = 0.93f))
            .background(accent.copy(alpha = if (macro.cardColor != 0L) 0.07f else 0f))
            .combinedClickable(onClick = onClick, onLongClick = { menuExpanded = true })
    ) {
```

to:

```kotlin
    Row(
        modifier
            .fillMaxWidth()
            .height(76.dp)
            .alpha(if (macro.enabled) 1f else 0.55f)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface.copy(alpha = 0.93f))
            .background(accent.copy(alpha = if (macro.cardColor != 0L) 0.07f else 0f))
            .combinedClickable(onClick = onClick, onLongClick = { menuExpanded = true })
    ) {
```

`Modifier.alpha` only affects drawing, not touch handling — the card stays fully tappable (including the switch, which needs to stay usable to re-enable a disabled macro) even at reduced opacity.

- [ ] **Step 3: Build**

```bash
cd "/Users/larssohl/Documents/Claude Work/Automatiq" && ./gradlew assembleDebug 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
cd "/Users/larssohl/Documents/Claude Work/Automatiq"
git add app/src/main/java/com/vibeactions/ui/common/MacroCard.kt
git commit -m "feat: MacroCard — dim whole card (not just accent bar) when macro is disabled"
```

---

## Task 4: Final verification + manual on-device checklist

**Files:** none (verification only).

- [ ] **Step 1: Full build + unit tests**

```bash
cd "/Users/larssohl/Documents/Claude Work/Automatiq" && ./gradlew clean assembleDebug testDebugUnitTest 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`, all existing unit tests still PASS (no test changes in this plan — no new business logic was introduced).

- [ ] **Step 2: Install and manually verify on a physical device**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Check each of the following on-device (cannot be verified from this Mac):

- One macro card per row, filling the screen width; long titles show more of the name than before (or fully, for typical names).
- Tapping a card's switch flips it immediately, and does **not** also open the editor or trigger the long-press menu.
- The switch state persists after closing and reopening the app.
- For a `SCHEDULED` macro: toggle off, confirm it does not fire at its scheduled time; toggle back on, confirm it resumes firing.
- For a `LOCATION` macro: toggle off then on, confirm no crash and the geofence re-registers (check via the existing macro log / a manual location trigger).
- A disabled card is visibly dimmed as a whole, not just the left accent bar.
- Drag-and-drop reorder (via the drag-handle icon) still works with one card per row.

- [ ] **Step 3: Push the branch**

```bash
cd "/Users/larssohl/Documents/Claude Work/Automatiq" && git push
```
