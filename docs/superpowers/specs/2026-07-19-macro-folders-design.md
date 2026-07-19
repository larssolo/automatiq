# Macro Folders — Design Spec
**Date:** 2026-07-19  
**App:** Automatiq (com.vibeactions)  
**DB version after:** 13

---

## Problem

With many macros the list loses overview. The user wants a **folder card**: a nameable card in the macro list that macros can be dragged into, grouping and ordering them properly.

## Decisions (from brainstorm)

1. **Accordion model** — tapping a folder card expands/collapses its macros inline in the same list. No separate folder screen.
2. **Drag-in + menu backup** — dropping a macro inside an expanded folder's block moves it into the folder; the long-press menu additionally gets "Move to folder…" / "Move to root" for collapsed folders and the root-corner case (see Drop rule).
3. **Master toggle** — the folder card has a switch that enables/disables every member macro at once (e.g. a "Holiday" folder).
4. **Data model A** — a dedicated `folders` table plus a nullable `folder_id` on macros (approaches B "name column" and C "folders as special macro rows" were rejected: B can't create an empty named folder first; C forces every engine query to filter out non-macros forever).

Out of scope (YAGNI): nested folders, per-folder log filtering, custom folder color picker (random palette color like macros), folder widgets, folder fields in the editor.

---

## Data model (Room v12 → v13, additive)

New table **`folders`**:

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT PK | UUID |
| `name` | TEXT NOT NULL | user-chosen, editable |
| `card_color` | INTEGER NOT NULL | random from `CardColorPalette` at creation |
| `sort_order` | INTEGER NOT NULL DEFAULT 0 | shares one ordering space with root macros |
| `expanded` | INTEGER NOT NULL DEFAULT 1 | accordion state, persisted |
| `created_at` | INTEGER NOT NULL | epoch ms |

`macros` gains **`folder_id` TEXT** (nullable; NULL = root). No SQL foreign key (table has none today); folder deletion explicitly nulls members' `folder_id`.

`MIGRATION_12_13`: `CREATE TABLE folders …` + `ALTER TABLE macros ADD COLUMN folder_id TEXT`.

**Engine isolation:** the firing engine (AlarmScheduler, MacroCatchUpWorker, receivers, IncomingReplyRouter, widgets, backup restore arming) reads macros by trigger type and id and never touches `folder_id`. Zero engine changes.

---

## Components

### Domain / data
- `Folder` domain model + mappers (`FolderEntity` ⇄ `Folder`).
- `FolderDao`: `observeAll` (Flow, ordered by `sort_order`), `getById`, `upsert`, `delete`, `updateExpanded`, `updateSortOrder`.
- `MacroDao` additions: `updateFolder(macroId, folderId)`, `clearFolder(folderId)` (members → NULL).
- `FolderRepository`: thin wrapper like `MacroRepository`.
- `ToggleFolderUseCase(folder, enabled)`: loops the folder's members through the existing `ToggleMacroUseCase` so alarms/geofences/state-monitor stay correct. Create/rename/delete/reorder go through `FolderRepository` directly from the ViewModel (no arming side effects).

### Pure layout logic — `util/FolderLayout.kt` (JVM-tested)
- `sealed interface ListRow` — `FolderRow(folder, memberCount, activeCount)` and `MacroRow(macro, folderId)`; stable keys `"f:<id>"` / `"m:<id>"`.
- `flattenRows(folders, macros): List<ListRow>` — root items (folders + folderless macros) merged by `sort_order` (ties: newest `created_at` first, matching today); each **expanded** folder's members follow immediately after it, ordered by their own `sort_order`.
- `resolveDrop(rows, fromIndex, toIndex): DropResult` — computes the dragged item's new `folderId` (macros only) and the full new ordering: root ordering (folders + root macros) and per-folder member ordering. Returned as id lists for the repositories to persist in one transaction.
- `folderSwitchState(members): Boolean` — switch shows ON iff **all** members enabled (partial shows OFF; subtitle carries the real count).

**Drop rule** (macros): the row immediately **above** the landing position decides — an expanded `FolderRow` or a `MacroRow` belonging to folder F ⇒ the macro joins F; anything else (root macro, collapsed folder, top of list) ⇒ root. Known corner: a drop directly below an expanded folder's last member lands *in* the folder — escape via "Move to root" in the menu or by collapsing the folder first. Dragging a **folder** moves the folder as a unit; it is collapsed for the duration of the drag and restored after; `resolveDrop` for folders only reorders root items (a folder can never land inside another folder).

### UI — `MacroListScreen` + new `ui/common/FolderCard.kt`
- The grid renders `List<ListRow>`; `ReorderableItem` keys use the prefixed ids.
- **FolderCard**: leaf-cut shape like MacroCard; folder icon + name (JetBrains Mono); subtitle `"4 macros · 3 active"` or `"Empty folder — drag macros in"` (app UI language is English); chevron rotates with `expanded`; accent = folder color; vein breathes when ≥1 member enabled; `ThemedSwitch` on the right wired to `ToggleFolderUseCase` (hidden when empty); drag handle like MacroCard. Tap = toggle `expanded` (persisted). Long-press menu: **Rename**, **Delete folder**.
- **Member macro cards**: indented 16 dp with the folder's color on the vein, so membership is readable at rest and during drags.
- **FAB**: tap opens a small anchored menu — *New macro* / *New folder*. New folder → name dialog → created expanded at the **top** of the list (`sort_order` = min−1).
- **Macro long-press menu** additions: "Move to folder…" (dialog listing folders + "Root"), shown only when at least one folder exists; members also get "Move to root".
- **Search**: unchanged behavior — flat filtered macro list, no folders, no drag.
- Duplicate keeps the source macro's `folder_id`.

### Backup — `util/BackupJson.kt`
- `Backup` gains `folders: List<FolderDto> = emptyList()`; `MacroDto` gains `folderId: String? = null`. Version stays 1 — `ignoreUnknownKeys` + defaults keep old files importing unchanged (everything lands in root) and old app versions reading new files simply drop folders.
- Import: restore folders first, then macros; a macro whose `folderId` matches no folder (hand-edited file) falls back to root.

---

## Edge cases

| Case | Behavior |
|---|---|
| Delete folder | Confirm dialog ("The macros move out to the root — nothing is deleted"); members → root; snackbar **Undo** restores folder + memberships (same pattern as macro delete) |
| Empty folder | Fully supported — create first, drag in later; switch hidden |
| Toggle on partial state | Switch shows OFF at partial; tap enables **all**; tap again disables all |
| Drop below last member | Lands in folder (documented corner); menu/collapse as escape |
| Search while folders exist | Flat macro results only |
| Import legacy backup | No `folders` array → all macros in root |
| Orphan `folderId` on import | Falls back to root |
| Widget bound to macro in folder | Unaffected (widgets bind macro ids) |
| Reorder during search | Still disabled (as today) |

---

## Testing (house style: pure JVM logic only)

New `FolderLayoutTest`: flatten with mixed root order, collapsed folders hide members, drop into folder (below header / between members / below last member), drop to root (below root macro / below collapsed folder / top), folder drag reorders roots only, switch-state all/partial/none/empty. `BackupJsonTest` additions: round-trip with folders + memberships; legacy file → root; orphan folderId → root. Expected ~12–15 new tests on top of the current 100. Compose/DAO layers untested (established pattern) — verified on device instead.

---

## File inventory

**New:** `data/db/entities/FolderEntity.kt`, `data/db/FolderDao.kt`, `data/repository/FolderRepository.kt`, `domain/usecase/ToggleFolderUseCase.kt`, `util/FolderLayout.kt`, `ui/common/FolderCard.kt`, `test …/FolderLayoutTest.kt`.  
**Modified:** `AppDatabase` (v13, entity list), `Migrations.kt`, `DatabaseModule`, `MacroEntity`/`Macro`/`Mappers` (+`folderId`), `MacroDao`, `MacroRepository`, `MacroListViewModel`, `MacroListScreen`, `MacroCard` (indent + menu items), `BackupJson`, `MacroJson` (DTO field), `BackupJsonTest`.
