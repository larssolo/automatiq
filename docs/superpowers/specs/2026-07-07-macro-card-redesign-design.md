# Macro Card Redesign — Design Spec
**Date:** 2026-07-07  
**App:** Automatiq (com.vibeactions)  
**DB version after:** unchanged (no schema change)

---

## Problem / Motivation

The macro list (`MacroListScreen.kt`) lays cards out in a 2-column grid (`LazyVerticalGrid(columns = GridCells.Fixed(2))`). Each card is therefore only about half the screen wide, so macro titles routinely get cut off mid-word (`MacroCard.kt`'s title `Text` is `maxLines = 1` with `TextOverflow.Ellipsis`). There is also no way to enable/disable a macro from the list — the only signal is the accent bar dimming to gray, and the only control is inside the editor bottom sheet.

Meanwhile the enable/disable machinery already exists end-to-end and is unused by the list UI: `Macro.enabled`, `ToggleMacroUseCase` (cancels/re-arms `AlarmScheduler`, registers/removes `GeofenceManager` correctly per trigger type), and `MacroListViewModel.onToggle(macro, enabled)`. The `ThemedSwitch` composable (the capsule toggle currently being restyled) is already used twice in `MacroEditorScreen.kt`.

---

## Scope

1. **Full-width cards** — one macro per row instead of two, so titles have roughly double the horizontal room before truncating.
2. **Inline on/off switch** — a `ThemedSwitch` on every card, wired to the existing toggle use case, so a macro can be enabled/disabled without opening the editor.
3. **Whole-card dim when disabled** — extend the existing "accent bar dims to gray" treatment to the whole card, so disabled state reads clearly at a glance.

**Out of scope** (tracked separately, see Follow-ups): cleaning up leftover Danish strings found while reading these files (`MacroCard.kt`'s dropdown menu, several `MacroEditorScreen.kt`/`SettingsScreen.kt` labels, and the Gemini prompt defaulting replies to Danish in `GeminiClient.kt`/`GeminiReplyWorker.kt`). Also out of scope: changing card height, wrapping titles to multiple lines, or altering the drag-reorder gesture.

---

## UI Changes

### `MacroListScreen.kt`
- `LazyVerticalGrid(columns = GridCells.Fixed(2))` → `GridCells.Fixed(1)`. No other change to the grid — `rememberReorderableLazyGridState`, `contentPadding`, and the 8dp arrangement spacing all carry over unchanged, since this only affects column count.
- Pass a new `onToggle` lambda down to `MacroCard`: `onToggle = { vm.onToggle(macro, it) }`.

### `MacroCard.kt`
- New parameter: `onToggle: (Boolean) -> Unit`.
- Layout (left → right), confirmed via mockup review: accent bar → title/subtitle column (unchanged, now with more room) → status column (status dot + optional "AI" chip, unchanged) → **new** `ThemedSwitch(checked = macro.enabled, onCheckedChange = onToggle)` → drag handle icon (unchanged, stays last).
- Whole-card dim: apply reduced alpha to the card's content when `!macro.enabled` (currently only the 4dp accent bar dims via `if (macro.enabled) accent else Outline`; extend this visual treatment to the card as a whole, e.g. via `Modifier.alpha(...)` on the row, tuned so it's clearly dimmed but text stays legible).

---

## Interaction Notes

- The switch's own `toggleable` modifier must consume its tap so it doesn't also fire the card's outer `combinedClickable` (which opens the editor) or long-press menu. Compose's nested-clickable semantics should already prevent bubbling here (same pattern as any icon button placed inside a clickable row), but verify explicitly on-device: tapping the switch should only flip enabled state, never also open the editor.
- Toggling from the card must produce the same behavior as toggling from the editor's existing "Enabled" switch (both ultimately go through `Macro.enabled` + persistence — the card takes the `ToggleMacroUseCase` path, the editor takes the `SaveMacroUseCase` path on save — so a `SCHEDULED` macro toggled off from the card must have its alarm cancelled, and toggled back on must re-arm; a `LOCATION` macro must register/remove its geofence correspondingly).

---

## Testing / Verification

No new business logic is introduced (the toggle path is pre-existing and already correct), so no new unit tests are required. This project has no Compose UI test harness (`androidTest` is empty), so verification is manual/on-device, consistent with prior on-device-only items in this codebase:

- Build debug APK, confirm one card per row fills the screen width and long titles show more (or all) of the name.
- Tap a card's switch: confirm it flips immediately, persists across app restart, and does **not** also open the editor.
- For a `SCHEDULED` macro: toggle off → confirm no alarm fires at its scheduled time; toggle back on → confirm it resumes firing.
- For a `LOCATION` macro: toggle off/on → confirm geofence is removed/re-registered (no crash, no duplicate registration).
- Drag-and-drop reorder still works with the single-column layout.
- Existing JVM unit tests (`./gradlew test`) continue to pass unmodified.

---

## Follow-ups (separate spec, not in this scope)

- Sweep leftover Danish strings: `MacroCard.kt` dropdown menu ("Slet"/"Kopiér"/"Send nu"), `MacroEditorScreen.kt` and `SettingsScreen.kt` labels/placeholders, and the Gemini prompt in `GeminiClient.kt`/`GeminiReplyWorker.kt` that currently instructs the AI to reply "på dansk" by default (a behavioral default, not just a label fix — worth a deliberate decision on what the new default should be).
