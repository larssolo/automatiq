# Macro Folders Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Nameable folder cards in the macro list that macros can be dragged into — accordion expand/collapse, drag-in with menu backup, and a master enable/disable switch per folder.

**Architecture:** New Room table `folders` + nullable `folder_id` on macros (v12→v13, additive). The list ViewModel flattens folders+macros into one row list via pure functions in `util/FolderLayout.kt` (JVM-tested); the drop position decides membership. The firing engine never reads `folder_id` — zero engine changes.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Room 2.6.1, Hilt, sh.calvin.reorderable 2.4.3, kotlinx-serialization. Spec: `docs/superpowers/specs/2026-07-19-macro-folders-design.md`.

## Global Constraints

- Working dir: `/Users/larssohl/Documents/Claude Work/automatiq-main`. Build: `./gradlew compileDebugKotlin`, tests: `./gradlew test` (baseline **100 green** — must never drop).
- Room migrations are additive only (`CREATE TABLE` / `ADD COLUMN`); `exportSchema=true` writes `app/schemas/...13.json` on build — commit it.
- ALL app-facing text is **English** (user rule, 2026-07-19). Danish template tokens (`{dato}` `{tid}` `{ugedag}` `{navn}` `{afsender}`) are functional identifiers existing macros depend on — they stay unchanged.
- Design language: leaf-cut corners (`RoundedCornerShape(topStart=18,topEnd=6,bottomEnd=18,bottomStart=6)`), JetBrains Mono names, breathing vein on enabled cards, accents from `CardColorPalette`.
- House test style: pure JVM logic gets tests (junit + kotlin-test, no mockk/robolectric); Compose/DAO glue does not.
- Do NOT touch: `MacroFirer`, `AlarmScheduler`, workers, receivers, `SmsDispatcher`, widget providers.
- Commits end with: `Co-Authored-By: WOZCODE <contact@withwoz.com>`

---

### Task 1: Folder data layer (entity, migration v13, DAOs, repository, editor passthrough)

**Files:**
- Create: `app/src/main/java/com/vibeactions/data/db/entities/FolderEntity.kt`
- Create: `app/src/main/java/com/vibeactions/data/db/FolderDao.kt`
- Create: `app/src/main/java/com/vibeactions/data/repository/FolderRepository.kt`
- Modify: `app/src/main/java/com/vibeactions/data/db/entities/MacroEntity.kt` (add `folder_id`)
- Modify: `app/src/main/java/com/vibeactions/domain/model/Macro.kt` (add `folderId`)
- Modify: `app/src/main/java/com/vibeactions/domain/model/Mappers.kt` (map both ways; add Folder mappers)
- Modify: `app/src/main/java/com/vibeactions/data/db/Migrations.kt` (MIGRATION_12_13)
- Modify: `app/src/main/java/com/vibeactions/data/db/AppDatabase.kt` (version 13, entity list, folderDao)
- Modify: `app/src/main/java/com/vibeactions/di/DatabaseModule.kt` (register migration + provide FolderDao)
- Modify: `app/src/main/java/com/vibeactions/data/db/MacroDao.kt` (getByFolder/updateFolder/clearFolder)
- Modify: `app/src/main/java/com/vibeactions/data/repository/MacroRepository.kt` (passthroughs)
- Modify: `app/src/main/java/com/vibeactions/ui/editor/MacroEditorViewModel.kt` (EditorState.folderId passthrough)
- Test: `app/src/test/java/com/vibeactions/ui/editor/EditorStateToMacroTest.kt`

**Interfaces:**
- Consumes: existing `Macro`, `MacroEntity`, repository patterns.
- Produces (later tasks rely on these exact names):
  - `data class Folder(id: String, name: String, cardColor: Long, sortOrder: Int = 0, expanded: Boolean = true, createdAt: Long = System.currentTimeMillis())` — new file `app/src/main/java/com/vibeactions/domain/model/Folder.kt` (add to this task's Create list).
  - `Macro.folderId: String?` (default null)
  - `FolderRepository`: `observeAll(): Flow<List<Folder>>`, `getById(id): Folder?`, `upsert(folder)`, `delete(folder)`, `setExpanded(id, expanded)`, `updateSortOrder(id, order)`
  - `MacroRepository`: `getByFolder(folderId): List<Macro>`, `setFolder(macroId, folderId: String?)`, `clearFolder(folderId)`, `updateSortOrder(id, order)`

- [ ] **Step 1: Write the failing editor-passthrough test**

Append to `EditorStateToMacroTest.kt`:

```kotlin
    @Test fun folderMembership_survivesEditRoundTrip() {
        // Editing a macro that lives in a folder must not kick it out on save.
        val state = EditorState(
            name = "Member", triggerType = TriggerType.SCHEDULED,
            recipients = listOf("+4512345678"), message = "Hej",
            folderId = "folder-1"
        )
        assertEquals("folder-1", state.toMacro("id-1").folderId)
    }
```

- [ ] **Step 2: Run it — expect compile failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.vibeactions.ui.editor.EditorStateToMacroTest" --console=plain`
Expected: FAILS — `unresolved reference: folderId`.

- [ ] **Step 3: Implement the data layer**

`app/src/main/java/com/vibeactions/domain/model/Folder.kt` (new):

```kotlin
package com.vibeactions.domain.model

/** A named group of macros shown as an accordion card in the list. Pure organization: the firing
 *  engine never reads folder membership. */
data class Folder(
    val id: String,
    val name: String,
    /** ARGB accent from CardColorPalette, chosen at creation. */
    val cardColor: Long,
    /** Shares one ordering space with root (folderless) macros. */
    val sortOrder: Int = 0,
    /** Accordion state; persisted so the list looks the same after restart. */
    val expanded: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
```

`app/src/main/java/com/vibeactions/data/db/entities/FolderEntity.kt` (new):

```kotlin
package com.vibeactions.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "card_color") val cardColor: Long,
    @ColumnInfo(name = "sort_order", defaultValue = "0") val sortOrder: Int = 0,
    @ColumnInfo(defaultValue = "1") val expanded: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
```

`MacroEntity.kt` — add as the last column:

```kotlin
    @ColumnInfo(name = "folder_id") val folderId: String? = null
```

`Macro.kt` — add as the last field:

```kotlin
    /** Folder this macro lives in; null = top level ("root") of the list. */
    val folderId: String? = null
```

`Mappers.kt` — add `folderId = folderId` to BOTH `MacroEntity.toDomain()` and `Macro.toEntity()`, and append:

```kotlin
fun FolderEntity.toDomain() = Folder(
    id = id, name = name, cardColor = cardColor,
    sortOrder = sortOrder, expanded = expanded, createdAt = createdAt
)

fun Folder.toEntity() = FolderEntity(
    id = id, name = name, cardColor = cardColor,
    sortOrder = sortOrder, expanded = expanded, createdAt = createdAt
)
```

(`Folder` import: same package.)

`Migrations.kt` — append:

```kotlin
/** v12 → v13: adds the `folders` table and `folder_id` on macros (NULL = root). Folder membership
 *  is pure list organization — the firing engine never reads it. */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS folders (" +
                "id TEXT NOT NULL PRIMARY KEY, " +
                "name TEXT NOT NULL, " +
                "card_color INTEGER NOT NULL, " +
                "sort_order INTEGER NOT NULL DEFAULT 0, " +
                "expanded INTEGER NOT NULL DEFAULT 1, " +
                "created_at INTEGER NOT NULL)"
        )
        db.execSQL("ALTER TABLE macros ADD COLUMN folder_id TEXT")
    }
}
```

`AppDatabase.kt` — replace the annotation/class header:

```kotlin
@Database(
    entities = [MacroEntity::class, MacroLogEntity::class, FolderEntity::class],
    version = 13,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun macroDao(): MacroDao
    abstract fun macroLogDao(): MacroLogDao
    abstract fun folderDao(): FolderDao
}
```

(import `FolderEntity`.)

`FolderDao.kt` (new):

```kotlin
package com.vibeactions.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.vibeactions.data.db.entities.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY sort_order ASC, created_at DESC")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: String): FolderEntity?

    @Upsert
    suspend fun upsert(folder: FolderEntity)

    @Delete
    suspend fun delete(folder: FolderEntity)

    @Query("UPDATE folders SET expanded = :expanded WHERE id = :id")
    suspend fun updateExpanded(id: String, expanded: Boolean)

    @Query("UPDATE folders SET sort_order = :order WHERE id = :id")
    suspend fun updateSortOrder(id: String, order: Int)
}
```

`MacroDao.kt` — append:

```kotlin
    @Query("SELECT * FROM macros WHERE folder_id = :folderId")
    suspend fun getByFolder(folderId: String): List<MacroEntity>

    @Query("UPDATE macros SET folder_id = :folderId WHERE id = :id")
    suspend fun updateFolder(id: String, folderId: String?)

    /** Folder deletion: members move to the root; nothing is deleted. */
    @Query("UPDATE macros SET folder_id = NULL WHERE folder_id = :folderId")
    suspend fun clearFolder(folderId: String)
```

`FolderRepository.kt` (new):

```kotlin
package com.vibeactions.data.repository

import com.vibeactions.data.db.FolderDao
import com.vibeactions.domain.model.Folder
import com.vibeactions.domain.model.toDomain
import com.vibeactions.domain.model.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderRepository @Inject constructor(private val dao: FolderDao) {
    fun observeAll(): Flow<List<Folder>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    suspend fun getById(id: String): Folder? = dao.getById(id)?.toDomain()
    suspend fun upsert(folder: Folder) = dao.upsert(folder.toEntity())
    suspend fun delete(folder: Folder) = dao.delete(folder.toEntity())
    suspend fun setExpanded(id: String, expanded: Boolean) = dao.updateExpanded(id, expanded)
    suspend fun updateSortOrder(id: String, order: Int) = dao.updateSortOrder(id, order)
}
```

`MacroRepository.kt` — append:

```kotlin
    suspend fun getByFolder(folderId: String): List<Macro> =
        dao.getByFolder(folderId).map { it.toDomain() }

    /** Moves a macro into a folder (or to the root with null). List organization only. */
    suspend fun setFolder(macroId: String, folderId: String?) = dao.updateFolder(macroId, folderId)

    suspend fun clearFolder(folderId: String) = dao.clearFolder(folderId)

    suspend fun updateSortOrder(id: String, order: Int) = dao.updateSortOrder(id, order)
```

`DatabaseModule.kt` — add `MIGRATION_12_13` to the `addMigrations(...)` list (import it) and append a provider:

```kotlin
    @Provides fun provideFolderDao(db: AppDatabase): FolderDao = db.folderDao()
```

(import `FolderDao`.)

`MacroEditorViewModel.kt` — three small edits:
1. `EditorState` gains (next to `sortOrder`): `val folderId: String? = null,`
2. `load()` passes it: add `folderId = m.folderId,` right after `sortOrder = m.sortOrder` (note: `load()` uses positional args up to `daysOfWeek`; add it as a NAMED arg alongside the existing named ones).
3. `EditorState.toMacro()` passes it: add `folderId = folderId,` next to `sortOrder = sortOrder,`.

- [ ] **Step 4: Run tests — all green**

Run: `./gradlew test --console=plain`
Expected: BUILD SUCCESSFUL, 101 tests (100 + the new one).

- [ ] **Step 5: Build once so the v13 schema JSON is generated, then commit**

Run: `./gradlew compileDebugKotlin --console=plain` → BUILD SUCCESSFUL, then confirm `app/schemas/com.vibeactions.data.db.AppDatabase/13.json` exists.

```bash
git add -A
git commit -m "feat(folders): data layer — folders table, folder_id on macros, Room v13

Co-Authored-By: WOZCODE <contact@withwoz.com>"
```

---

### Task 2: Pure list layout logic — `util/FolderLayout.kt` (TDD)

**Files:**
- Create: `app/src/main/java/com/vibeactions/util/FolderLayout.kt`
- Test: `app/src/test/java/com/vibeactions/util/FolderLayoutTest.kt`

**Interfaces:**
- Consumes: `Folder`, `Macro` (Task 1).
- Produces (Tasks 4-5 rely on exactly these):
  - `sealed interface ListRow { val key: String }`
  - `data class FolderRow(folder: Folder, memberCount: Int, activeCount: Int) : ListRow` — key `"f:<id>"`
  - `data class MacroRow(macro: Macro) : ListRow` — key `"m:<id>"`
  - `fun flattenRows(folders: List<Folder>, macros: List<Macro>): List<ListRow>`
  - `fun hideMembers(rows: List<ListRow>, folderId: String): List<ListRow>`
  - `data class DropResult(rows: List<ListRow>, movedMacroFolderId: String?)`
  - `fun resolveDrop(rows: List<ListRow>, draggedKey: String): DropResult`
  - `data class LayoutOrders(folderOrder: Map<String, Int>, rootMacroOrder: Map<String, Int>, memberOrder: Map<String, Map<String, Int>>)`
  - `fun layoutOrders(rows: List<ListRow>): LayoutOrders`
  - `fun folderSwitchOn(memberCount: Int, activeCount: Int): Boolean`

- [ ] **Step 1: Write the failing tests**

`FolderLayoutTest.kt` (new file, complete):

```kotlin
package com.vibeactions.util

import com.vibeactions.domain.model.Folder
import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderLayoutTest {

    private fun macro(id: String, sort: Int, folderId: String? = null, enabled: Boolean = true) = Macro(
        id = id, name = id, triggerType = TriggerType.MANUAL, scheduledTime = null,
        recipients = listOf("+4512345678"), messageBody = "x", enabled = enabled,
        sortOrder = sort, createdAt = 0L, folderId = folderId
    )

    private fun folder(id: String, sort: Int, expanded: Boolean = true) =
        Folder(id = id, name = id, cardColor = 1L, sortOrder = sort, expanded = expanded, createdAt = 0L)

    private fun keys(rows: List<ListRow>) = rows.map { it.key }

    // ── flattenRows ──

    @Test fun flatten_mergesRootsBySortOrder_membersFollowExpandedFolder() {
        val rows = flattenRows(
            folders = listOf(folder("A", sort = 1)),
            macros = listOf(
                macro("root1", sort = 0), macro("root2", sort = 2),
                macro("m1", sort = 0, folderId = "A"), macro("m2", sort = 1, folderId = "A")
            )
        )
        assertEquals(listOf("m:root1", "f:A", "m:m1", "m:m2", "m:root2"), keys(rows))
        assertEquals(2, (rows[1] as FolderRow).memberCount)
    }

    @Test fun flatten_collapsedFolderHidesMembers_butCountsThem() {
        val rows = flattenRows(
            folders = listOf(folder("A", sort = 0, expanded = false)),
            macros = listOf(
                macro("m1", sort = 0, folderId = "A", enabled = true),
                macro("m2", sort = 1, folderId = "A", enabled = false),
                macro("root1", sort = 1)
            )
        )
        assertEquals(listOf("f:A", "m:root1"), keys(rows))
        assertEquals(2, (rows[0] as FolderRow).memberCount)
        assertEquals(1, (rows[0] as FolderRow).activeCount)
    }

    @Test fun flatten_orphanMemberOfUnknownFolder_showsAtRoot() {
        // Hand-edited import can leave a folderId with no folder — never lose the macro.
        val rows = flattenRows(folders = emptyList(), macros = listOf(macro("m1", 0, folderId = "ghost")))
        assertEquals(listOf("m:m1"), keys(rows))
    }

    // ── hideMembers (folder drag) ──

    @Test fun hideMembers_removesOnlyThatFoldersMacroRows() {
        val rows = flattenRows(
            folders = listOf(folder("A", 0), folder("B", 2)),
            macros = listOf(macro("a1", 0, "A"), macro("b1", 0, "B"), macro("root1", 1))
        )
        assertEquals(listOf("f:A", "m:root1", "f:B", "m:b1"), keys(hideMembers(rows, "A")))
    }

    // ── resolveDrop: membership from the row above ──

    @Test fun drop_directlyUnderExpandedFolderHeader_joinsFolder() {
        val rows = flattenRows(
            folders = listOf(folder("A", 0)),
            macros = listOf(macro("m1", 0, "A"), macro("root1", 1))
        ) // f:A, m:m1, m:root1  → drag root1 up between header and m1
        val moved = listOf(rows[0], rows[2], rows[1])
        val result = resolveDrop(moved, draggedKey = "m:root1")
        assertEquals("A", result.movedMacroFolderId)
        assertEquals("A", (result.rows[1] as MacroRow).macro.folderId)
    }

    @Test fun drop_belowMemberOfFolder_joinsSameFolder() {
        val rows = flattenRows(
            folders = listOf(folder("A", 0)),
            macros = listOf(macro("m1", 0, "A"), macro("root1", 1))
        ) // drag root1 below m1 (still inside the block per the spec's corner rule)
        val moved = listOf(rows[0], rows[1], rows[2]) // root1 already sits right after m1
        assertEquals("A", resolveDrop(moved, "m:root1").movedMacroFolderId)
    }

    @Test fun drop_atTopOfList_goesToRoot() {
        val rows = flattenRows(
            folders = listOf(folder("A", 0)),
            macros = listOf(macro("m1", 0, "A"))
        )
        val moved = listOf(rows[1], rows[0]) // m1 dragged above the folder header
        val result = resolveDrop(moved, "m:m1")
        assertNull(result.movedMacroFolderId)
        assertNull((result.rows[0] as MacroRow).macro.folderId)
    }

    @Test fun drop_belowCollapsedFolder_goesToRoot() {
        val rows = flattenRows(
            folders = listOf(folder("A", 0, expanded = false)),
            macros = listOf(macro("a1", 0, "A"), macro("root1", 1), macro("root2", 2))
        ) // f:A, m:root1, m:root2 → drag root2 up right under the collapsed folder
        val moved = listOf(rows[0], rows[2], rows[1])
        assertNull(resolveDrop(moved, "m:root2").movedMacroFolderId)
    }

    @Test fun drop_belowRootMacro_staysRoot() {
        val rows = flattenRows(
            folders = emptyList(),
            macros = listOf(macro("root1", 0), macro("root2", 1))
        )
        assertNull(resolveDrop(listOf(rows[1], rows[0]), "m:root2").movedMacroFolderId)
    }

    @Test fun drop_draggedFolder_neverChangesMembership() {
        val rows = flattenRows(
            folders = listOf(folder("A", 0, expanded = false), folder("B", 1, expanded = false)),
            macros = listOf(macro("a1", 0, "A"))
        )
        val result = resolveDrop(listOf(rows[1], rows[0]), "f:A")
        assertNull(result.movedMacroFolderId)
    }

    // ── layoutOrders ──

    @Test fun layoutOrders_assignsRootSpaceAndPerFolderSpace() {
        val rows = flattenRows(
            folders = listOf(folder("A", 0)),
            macros = listOf(macro("m1", 0, "A"), macro("m2", 1, "A"), macro("root1", 1))
        ) // f:A, m:m1, m:m2, m:root1
        val orders = layoutOrders(rows)
        assertEquals(0, orders.folderOrder["A"])
        assertEquals(1, orders.rootMacroOrder["root1"])
        assertEquals(mapOf("m1" to 0, "m2" to 1), orders.memberOrder["A"])
    }

    @Test fun layoutOrders_skipsHiddenMembersOfCollapsedFolder() {
        val rows = flattenRows(
            folders = listOf(folder("A", 0, expanded = false)),
            macros = listOf(macro("a1", 0, "A"), macro("root1", 1))
        )
        val orders = layoutOrders(rows)
        assertFalse(orders.memberOrder.containsKey("A")) // untouched, keeps stored order
        assertEquals(1, orders.rootMacroOrder["root1"])
    }

    // ── folderSwitchOn ──

    @Test fun switch_onOnlyWhenAllMembersEnabled() {
        assertTrue(folderSwitchOn(memberCount = 2, activeCount = 2))
        assertFalse(folderSwitchOn(memberCount = 2, activeCount = 1))   // partial shows OFF
        assertFalse(folderSwitchOn(memberCount = 0, activeCount = 0))   // empty folder
    }
}
```

- [ ] **Step 2: Run — expect compile failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.vibeactions.util.FolderLayoutTest" --console=plain`
Expected: FAILS — `unresolved reference: flattenRows` etc.

- [ ] **Step 3: Implement `FolderLayout.kt`**

```kotlin
package com.vibeactions.util

import com.vibeactions.domain.model.Folder
import com.vibeactions.domain.model.Macro

/**
 * Pure layout logic for the folder-aware macro list. The screen shows one flat row list; these
 * functions build it, hide members while a folder is dragged, decide membership on drop, and
 * derive the sort orders to persist. Kept free of Android/Compose so it is JVM-testable.
 */
sealed interface ListRow { val key: String }

data class FolderRow(val folder: Folder, val memberCount: Int, val activeCount: Int) : ListRow {
    override val key: String get() = "f:${folder.id}"
}

data class MacroRow(val macro: Macro) : ListRow {
    override val key: String get() = "m:${macro.id}"
}

/** Root items (folders + folderless macros) merged in one sort space; each EXPANDED folder's
 *  members follow directly after it. A member whose folder no longer exists shows at root —
 *  a macro must never disappear from the list. */
fun flattenRows(folders: List<Folder>, macros: List<Macro>): List<ListRow> {
    val folderIds = folders.map { it.id }.toSet()
    val members = macros.filter { it.folderId in folderIds }.groupBy { it.folderId!! }
    val roots: List<Any> = (folders + macros.filter { it.folderId !in folderIds })
        .sortedWith(
            compareBy<Any> { if (it is Folder) it.sortOrder else (it as Macro).sortOrder }
                .thenByDescending { if (it is Folder) it.createdAt else (it as Macro).createdAt }
        )
    val rows = mutableListOf<ListRow>()
    roots.forEach { item ->
        when (item) {
            is Folder -> {
                val m = members[item.id].orEmpty()
                    .sortedWith(compareBy<Macro> { it.sortOrder }.thenByDescending { it.createdAt })
                rows += FolderRow(item, m.size, m.count { it.enabled })
                if (item.expanded) m.forEach { rows += MacroRow(it) }
            }
            is Macro -> rows += MacroRow(item)
        }
    }
    return rows
}

/** While a folder card is dragged it is shown collapsed: its member rows are hidden. */
fun hideMembers(rows: List<ListRow>, folderId: String): List<ListRow> =
    rows.filterNot { it is MacroRow && it.macro.folderId == folderId }

/** Outcome of a drop: the row list with membership applied, and (for a dragged macro) its new
 *  folder — null for root. Folders never change membership by dragging. */
data class DropResult(val rows: List<ListRow>, val movedMacroFolderId: String?)

/**
 * The row immediately ABOVE the dragged item's landing position decides membership:
 * an expanded folder header or one of its members ⇒ the macro joins that folder;
 * anything else (root macro, collapsed folder, top of list) ⇒ root.
 */
fun resolveDrop(rows: List<ListRow>, draggedKey: String): DropResult {
    val index = rows.indexOfFirst { it.key == draggedKey }
    if (index < 0) return DropResult(rows, null)
    val dragged = rows[index]
    if (dragged !is MacroRow) return DropResult(rows, null)
    val above = rows.getOrNull(index - 1)
    val newFolderId = when {
        above is FolderRow && above.folder.expanded -> above.folder.id
        above is MacroRow && above.macro.folderId != null -> above.macro.folderId
        else -> null
    }
    val updated = rows.toMutableList()
    updated[index] = MacroRow(dragged.macro.copy(folderId = newFolderId))
    return DropResult(updated, newFolderId)
}

/** Sort orders to persist after a drop. Root items (folders + root macros) share one index space;
 *  each folder's visible members get their own 0-based space. Members of collapsed folders are
 *  not in the rows and are deliberately absent — their stored order stays untouched. */
data class LayoutOrders(
    val folderOrder: Map<String, Int>,
    val rootMacroOrder: Map<String, Int>,
    val memberOrder: Map<String, Map<String, Int>>
)

fun layoutOrders(rows: List<ListRow>): LayoutOrders {
    val folderOrder = linkedMapOf<String, Int>()
    val rootMacroOrder = linkedMapOf<String, Int>()
    val memberOrder = linkedMapOf<String, MutableMap<String, Int>>()
    var rootIndex = 0
    rows.forEach { row ->
        when (row) {
            is FolderRow -> folderOrder[row.folder.id] = rootIndex++
            is MacroRow -> {
                val fid = row.macro.folderId
                if (fid == null) rootMacroOrder[row.macro.id] = rootIndex++
                else {
                    val m = memberOrder.getOrPut(fid) { linkedMapOf() }
                    m[row.macro.id] = m.size
                }
            }
        }
    }
    return LayoutOrders(folderOrder, rootMacroOrder, memberOrder)
}

/** The folder card's switch shows ON only when every member is enabled (empty = OFF). Partial
 *  state shows OFF — tapping then enables ALL, which is the predictable repair action. */
fun folderSwitchOn(memberCount: Int, activeCount: Int): Boolean =
    memberCount > 0 && activeCount == memberCount
```

- [ ] **Step 4: Run the test class — all green**

Run: `./gradlew :app:testDebugUnitTest --tests "com.vibeactions.util.FolderLayoutTest" --console=plain`
Expected: BUILD SUCCESSFUL, 13 tests pass.

- [ ] **Step 5: Full suite + commit**

Run: `./gradlew test --console=plain` → 114 tests green.

```bash
git add app/src/main/java/com/vibeactions/util/FolderLayout.kt app/src/test/java/com/vibeactions/util/FolderLayoutTest.kt
git commit -m "feat(folders): pure list layout — flatten, drop rule, layout orders (TDD)

Co-Authored-By: WOZCODE <contact@withwoz.com>"
```

---

### Task 3: Backup round-trip with folders (TDD)

**Files:**
- Modify: `app/src/main/java/com/vibeactions/util/MacroJson.kt` (MacroDto.folderId)
- Modify: `app/src/main/java/com/vibeactions/util/BackupJson.kt` (FolderDto, Backup.folders, orphan guard)
- Modify: `app/src/main/java/com/vibeactions/ui/settings/SettingsViewModel.kt` (export/import folders)
- Test: `app/src/test/java/com/vibeactions/util/BackupJsonTest.kt`

**Interfaces:**
- Consumes: `Folder` (Task 1), existing `MacroDto`/`Backup`/`parseBackup`.
- Produces: `exportBackup(macros, folders: List<Folder>, settings)` (signature change), `ParsedBackup.folders: List<Folder>`; `parseBackup` nulls any `folderId` that matches no folder in the same file.

- [ ] **Step 1: Write the failing tests**

Append to `BackupJsonTest.kt` (the test code below builds its Macros inline and is complete):

```kotlin
    @Test fun backup_roundTripsFoldersAndMembership() {
        val folder = Folder(id = "f1", name = "Holiday", cardColor = 5L, sortOrder = 2,
            expanded = false, createdAt = 7L)
        val member = Macro(
            id = "m1", name = "Member", triggerType = TriggerType.MANUAL, scheduledTime = null,
            recipients = listOf("+4512345678"), messageBody = "x", folderId = "f1"
        )
        val parsed = parseBackup(exportBackup(listOf(member), listOf(folder), emptyMap()))
        assertEquals(listOf(folder), parsed.folders)
        assertEquals("f1", parsed.macros.single().folderId)
    }

    @Test fun backup_orphanFolderIdFallsBackToRoot() {
        val orphan = Macro(
            id = "m1", name = "Orphan", triggerType = TriggerType.MANUAL, scheduledTime = null,
            recipients = listOf("+4512345678"), messageBody = "x", folderId = "ghost"
        )
        val parsed = parseBackup(exportBackup(listOf(orphan), emptyList(), emptyMap()))
        assertNull(parsed.macros.single().folderId)
    }

    @Test fun backup_legacyFileWithoutFoldersImportsToRoot() {
        val legacy = """{"version":1,"macros":[],"settings":{}}"""
        assertTrue(parseBackup(legacy).folders.isEmpty())
    }
```

(Add imports: `com.vibeactions.domain.model.Folder`, `assertNull`, `assertTrue` as needed.)

- [ ] **Step 2: Run — expect compile failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.vibeactions.util.BackupJsonTest" --console=plain`
Expected: FAILS — `exportBackup` has no folders parameter / `ParsedBackup.folders` missing.

- [ ] **Step 3: Implement**

`MacroJson.kt`:
- `MacroDto` gains `val folderId: String? = null` (last field).
- `Macro.toDto()` gains `folderId = folderId`.
- `MacroDto.toMacro()` gains `folderId = folderId`.

`BackupJson.kt` — replace the file's data classes and functions with:

```kotlin
@Serializable
internal data class FolderDto(
    val id: String, val name: String, val cardColor: Long = 0L,
    val sortOrder: Int = 0, val expanded: Boolean = true, val createdAt: Long = 0L
)

internal fun Folder.toDto() = FolderDto(id, name, cardColor, sortOrder, expanded, createdAt)
internal fun FolderDto.toFolder() = Folder(id, name, cardColor, sortOrder, expanded, createdAt)

@Serializable
internal data class Backup(
    val version: Int = 1,
    val macros: List<MacroDto> = emptyList(),
    val folders: List<FolderDto> = emptyList(),
    val settings: Map<String, String> = emptyMap()
)

private val backupJson = Json { prettyPrint = true; ignoreUnknownKeys = true }

fun exportBackup(macros: List<Macro>, folders: List<Folder>, settings: Map<String, String>): String =
    backupJson.encodeToString(
        Backup.serializer(),
        Backup(version = 1, macros = macros.map { it.toDto() },
            folders = folders.map { it.toDto() }, settings = settings)
    )

/** Parsed backup: macros, folders, and the settings map to re-apply. */
class ParsedBackup(val macros: List<Macro>, val folders: List<Folder>, val settings: Map<String, String>)

/**
 * Parses either format: a bare macro array (legacy `exportMacros` output) or a full [Backup]
 * object. A macro whose folderId matches no folder in the same file (hand-edited) falls back to
 * root — a macro must never vanish behind a ghost folder.
 */
fun parseBackup(text: String): ParsedBackup {
    val firstChar = text.trimStart().firstOrNull()
    if (firstChar == '[') return ParsedBackup(importMacros(text), emptyList(), emptyMap())
    val backup = backupJson.decodeFromString(Backup.serializer(), text)
    val folders = backup.folders.map { it.toFolder() }
    val ids = folders.map { it.id }.toSet()
    val macros = backup.macros.map { it.toMacro() }
        .map { if (it.folderId != null && it.folderId !in ids) it.copy(folderId = null) else it }
    return ParsedBackup(macros, folders, backup.settings)
}
```

(Add `import com.vibeactions.domain.model.Folder`. The legacy-array branch returns empty folders/settings as shown.)

`SettingsViewModel.kt`:
- Inject `private val folderRepo: FolderRepository` in the constructor.
- `exportBackup(...)`: also collect folders and pass them:

```kotlin
    fun exportBackup(includeApiKey: Boolean, onReady: (String) -> Unit) = viewModelScope.launch {
        val macros = repo.observeAll().first()
        val folders = folderRepo.observeAll().first()
        onReady(com.vibeactions.util.exportBackup(macros, folders, collectSettings(includeApiKey)))
    }
```

- `importBackup(...)`: restore folders BEFORE macros (membership needs them for the orphan guard already applied at parse; upserting first also keeps the list coherent while saves stream in):

```kotlin
        result.getOrNull()?.let { parsed ->
            parsed.folders.forEach { folderRepo.upsert(it) }
            parsed.macros.forEach { save(it) }
            applySettings(parsed.settings)
        }
```

- [ ] **Step 4: Run tests — all green**

Run: `./gradlew test --console=plain`
Expected: BUILD SUCCESSFUL, 117 tests.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(folders): backup round-trip — folders array, folderId on macros, orphan guard (TDD)

Co-Authored-By: WOZCODE <contact@withwoz.com>"
```

---

### Task 4: ViewModel wiring — rows flow, drop persistence, folder operations

**Files:**
- Create: `app/src/main/java/com/vibeactions/domain/usecase/ToggleFolderUseCase.kt`
- Modify: `app/src/main/java/com/vibeactions/ui/macrolist/MacroListViewModel.kt`

**Interfaces:**
- Consumes: `flattenRows`/`resolveDrop`/`layoutOrders`/`folderSwitchOn` (Task 2), `FolderRepository`/`MacroRepository` additions (Task 1), existing `ToggleMacroUseCase`, `randomCardColor()`.
- Produces (Task 5 relies on): `vm.rows: StateFlow<List<ListRow>>`, `vm.folders: StateFlow<List<Folder>>`, `vm.onDrop(rows, draggedKey)`, `vm.onToggleExpanded(folder)`, `vm.onCreateFolder(name)`, `vm.onRenameFolder(folder, name)`, `suspend vm.deleteFolder(folder): List<String>`, `vm.onUndoDeleteFolder(folder, memberIds)`, `vm.onToggleFolder(folder, enabled)`, `vm.onMoveToFolder(macro, folderId: String?)`. Existing macro handlers stay unchanged.

- [ ] **Step 1: Implement `ToggleFolderUseCase.kt`**

```kotlin
package com.vibeactions.domain.usecase

import com.vibeactions.data.repository.MacroRepository
import javax.inject.Inject

/** Enables/disables every macro in a folder at once (the folder card's master switch). Each member
 *  goes through ToggleMacroUseCase so alarms, geofences and the state monitor stay in sync. */
class ToggleFolderUseCase @Inject constructor(
    private val macroRepo: MacroRepository,
    private val toggleMacro: ToggleMacroUseCase
) {
    suspend operator fun invoke(folderId: String, enabled: Boolean) {
        macroRepo.getByFolder(folderId).forEach { toggleMacro(it, enabled) }
    }
}
```

- [ ] **Step 2: Extend `MacroListViewModel`**

Add constructor params `private val folderRepo: FolderRepository` and `private val toggleFolder: ToggleFolderUseCase`. Keep `macros` StateFlow (search mode uses it). Add:

```kotlin
    val folders: StateFlow<List<Folder>> =
        folderRepo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The folder-aware flat row list the screen renders (folders + members + root macros). */
    val rows: StateFlow<List<ListRow>> =
        combine(folderRepo.observeAll(), repo.observeAll()) { folders, macros ->
            flattenRows(folders, macros)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Persists a finished drag: the dragged macro's (possibly new) folder plus every visible
     *  row's order. Membership was decided by resolveDrop from the landing position. */
    fun onDrop(rows: List<ListRow>, draggedKey: String) = viewModelScope.launch {
        val result = resolveDrop(rows, draggedKey)
        if (draggedKey.startsWith("m:")) {
            repo.setFolder(draggedKey.removePrefix("m:"), result.movedMacroFolderId)
        }
        val orders = layoutOrders(result.rows)
        orders.folderOrder.forEach { (id, order) -> folderRepo.updateSortOrder(id, order) }
        orders.rootMacroOrder.forEach { (id, order) -> repo.updateSortOrder(id, order) }
        orders.memberOrder.forEach { (_, members) ->
            members.forEach { (id, order) -> repo.updateSortOrder(id, order) }
        }
    }

    fun onToggleExpanded(folder: Folder) =
        viewModelScope.launch { folderRepo.setExpanded(folder.id, !folder.expanded) }

    /** New folders land at the very top of the list, expanded and ready to drag into. */
    fun onCreateFolder(name: String) = viewModelScope.launch {
        val minRoot = minOf(
            folders.value.minOfOrNull { it.sortOrder } ?: 0,
            macros.value.filter { it.folderId == null }.minOfOrNull { it.sortOrder } ?: 0
        )
        folderRepo.upsert(
            Folder(id = UUID.randomUUID().toString(), name = name.trim(),
                cardColor = randomCardColor(), sortOrder = minRoot - 1)
        )
    }

    fun onRenameFolder(folder: Folder, name: String) =
        viewModelScope.launch { folderRepo.upsert(folder.copy(name = name.trim())) }

    /** Deletes the folder, moving members to root. Returns their ids so the screen's Undo
     *  snackbar can restore the memberships. */
    suspend fun deleteFolder(folder: Folder): List<String> {
        val memberIds = repo.getByFolder(folder.id).map { it.id }
        repo.clearFolder(folder.id)
        folderRepo.delete(folder)
        return memberIds
    }

    fun onUndoDeleteFolder(folder: Folder, memberIds: List<String>) = viewModelScope.launch {
        folderRepo.upsert(folder)
        memberIds.forEach { repo.setFolder(it, folder.id) }
    }

    fun onToggleFolder(folder: Folder, enabled: Boolean) =
        viewModelScope.launch { toggleFolder(folder.id, enabled) }

    /** Menu-driven move ("Move to folder…" / "Move to root"); null = root. */
    fun onMoveToFolder(macro: Macro, folderId: String?) =
        viewModelScope.launch { repo.setFolder(macro.id, folderId) }
```

Delete the now-unused `onReorder`/`persistOrder` pair: remove `fun onReorder(...)` from the ViewModel and `persistOrder(...)` from `MacroRepository` (its DAO method `updateSortOrder` stays — `onDrop` uses it). Imports to add: `Folder`, `ListRow`, `flattenRows`, `resolveDrop`, `layoutOrders`, `FolderRepository`, `ToggleFolderUseCase`, `combine`.

- [ ] **Step 3: Compile + full suite**

`MacroListScreen` still calls `vm.onReorder(...)` until Task 5 rewrites it. To keep this task green: replace `onReorder`'s body with a no-op (`fun onReorder(orderedIds: List<String>) = Unit`) in this task, and remove `MacroRepository.persistOrder` now (nothing else calls it). Task 5 deletes the no-op together with its call site.

Run: `./gradlew compileDebugKotlin test --console=plain`
Expected: BUILD SUCCESSFUL, 117 tests.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat(folders): list ViewModel — rows flow, drop persistence, folder ops, master toggle

Co-Authored-By: WOZCODE <contact@withwoz.com>"
```

---

### Task 5: UI — FolderCard, folder-aware list, FAB menu, move menus, dialogs

**Files:**
- Create: `app/src/main/java/com/vibeactions/ui/common/FolderCard.kt`
- Modify: `app/src/main/java/com/vibeactions/ui/common/MacroCard.kt` (vein override + move menu items)
- Modify: `app/src/main/java/com/vibeactions/ui/macrolist/MacroListScreen.kt` (render rows, drag, FAB menu, dialogs)

**Interfaces:**
- Consumes: everything Task 4 produced; `LeafShape` stays private to MacroCard — FolderCard defines its own identical `private val LeafShape`.
- Produces: `FolderCard(folder, memberCount, activeCount, switchOn, onClick, onToggleAll, onRename, onDelete, modifier, dragHandleModifier)`; `MacroCard` gains `veinColor: Color? = null`, `onMoveToFolder: (() -> Unit)? = null`, `onMoveToRoot: (() -> Unit)? = null`.

- [ ] **Step 1: `FolderCard.kt` (new, complete)**

```kotlin
package com.vibeactions.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibeactions.domain.model.Folder
import com.vibeactions.ui.theme.JetBrainsMono
import com.vibeactions.ui.theme.OnSurface
import com.vibeactions.ui.theme.OnSurfaceVariant
import com.vibeactions.ui.theme.Outline
import com.vibeactions.ui.theme.Surface

/** Leaf-cut signature shape (mirrors MacroCard's). */
private val LeafShape = RoundedCornerShape(
    topStart = 18.dp, topEnd = 6.dp, bottomEnd = 18.dp, bottomStart = 6.dp
)

/**
 * Accordion folder card. Tap = expand/collapse; long-press = Rename/Delete; the switch
 * enables/disables every member at once (ON only when ALL members are enabled).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderCard(
    folder: Folder,
    memberCount: Int,
    activeCount: Int,
    switchOn: Boolean,
    onClick: () -> Unit,
    onToggleAll: (Boolean) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier
) {
    val accent = Color(folder.cardColor)
    var menuExpanded by remember { mutableStateOf(false) }
    val chevron by animateFloatAsState(if (folder.expanded) 180f else 0f, label = "chevron")

    Row(
        modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(LeafShape)
            .background(Surface.copy(alpha = BackgroundSetting.cardOpacity))
            .background(
                Brush.horizontalGradient(
                    listOf(accent.copy(alpha = 0.16f), accent.copy(alpha = 0.03f))
                )
            )
            .combinedClickable(onClick = onClick, onLongClick = { menuExpanded = true })
    ) {
        val veinColor = if (activeCount > 0) {
            val breath = rememberInfiniteTransition(label = "folderVein")
            val a by breath.animateFloat(
                0.45f, 1f,
                infiniteRepeatable(tween(3600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "folderVeinAlpha"
            )
            accent.copy(alpha = a)
        } else Outline
        Box(Modifier.width(4.dp).fillMaxHeight().background(veinColor))

        Icon(
            Icons.Default.Folder, contentDescription = null, tint = accent,
            modifier = Modifier.align(Alignment.CenterVertically).padding(start = 12.dp).size(20.dp)
        )
        Column(
            Modifier.weight(1f).fillMaxHeight().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                folder.name, fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium,
                color = OnSurface, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                if (memberCount == 0) "Empty folder — drag macros in"
                else "$memberCount macros · $activeCount active",
                color = OnSurfaceVariant, fontSize = 12.sp, maxLines = 1
            )
        }
        if (memberCount > 0) {
            Box(Modifier.fillMaxHeight().padding(end = 2.dp), contentAlignment = Alignment.Center) {
                ThemedSwitch(checked = switchOn, onCheckedChange = onToggleAll)
            }
        }
        Icon(
            Icons.Default.ExpandMore, contentDescription = if (folder.expanded) "Collapse" else "Expand",
            tint = OnSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterVertically).rotate(chevron)
        )
        Box(Modifier.fillMaxHeight().padding(end = 4.dp), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.DragHandle, contentDescription = "Reorder", tint = OnSurfaceVariant,
                modifier = dragHandleModifier.padding(6.dp)
            )
        }
        Box {
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = { menuExpanded = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text("Delete folder") },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = { menuExpanded = false; onDelete() }
                )
            }
        }
    }
}
```

- [ ] **Step 2: `MacroCard` additions**

Signature: add after `onToggle`:

```kotlin
    veinColor: Color? = null,
    onMoveToFolder: (() -> Unit)? = null,
    onMoveToRoot: (() -> Unit)? = null,
```

(`Color` = `androidx.compose.ui.graphics.Color`, import it.) In the vein block, the enabled branch becomes `(veinColor ?: accent).copy(alpha = veinAlpha)` and the disabled branch stays `Outline`. In the dropdown menu, after the "Send now" item add:

```kotlin
                if (onMoveToFolder != null) DropdownMenuItem(
                    text = { Text("Move to folder…") },
                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    onClick = { menuExpanded = false; onMoveToFolder() }
                )
                if (onMoveToRoot != null) DropdownMenuItem(
                    text = { Text("Move to root") },
                    leadingIcon = { Icon(Icons.Default.FolderOff, contentDescription = null) },
                    onClick = { menuExpanded = false; onMoveToRoot() }
                )
```

(imports: `androidx.compose.material.icons.filled.Folder`, `androidx.compose.material.icons.filled.FolderOff`.)

- [ ] **Step 3: Rewrite the list section of `MacroListScreen`**

Replace state/collection at the top of the composable: keep `macros` (search mode), add:

```kotlin
    val vmRows by vm.rows.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    var rows by remember { mutableStateOf(vmRows) }
    LaunchedEffect(vmRows) { rows = vmRows }
    var draggedKey by remember { mutableStateOf<String?>(null) }
    var showCreateFolder by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Folder?>(null) }
    var deleteTarget by remember { mutableStateOf<Folder?>(null) }
    var moveTarget by remember { mutableStateOf<Macro?>(null) }
    var fabMenu by remember { mutableStateOf(false) }
```

Reorder state now moves rows (and hides members when a folder drag starts):

```kotlin
    val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
        var current = rows
        val key = current.getOrNull(from.index)?.key
        if (key != null && key.startsWith("f:") && draggedKey != key) {
            draggedKey = key
            current = hideMembers(current, key.removePrefix("f:"))
        } else if (key != null && draggedKey == null) draggedKey = key
        val fromIdx = current.indexOfFirst { it.key == key }
        rows = current.toMutableList().apply {
            add(to.index.coerceAtMost(size - 1), removeAt(fromIdx.coerceAtLeast(0)))
        }
    }
```

**Note:** the reorderable callback delivers indices into the CURRENT list; hiding members changes indices mid-gesture. If gesture jitter appears on device, simplify: hide members already at `onDragStopped`-less approach — instead collapse via VM before drag is impossible; acceptable fallback is to skip live-hiding and only visually collapse via `alpha` — but FIRST try the simple version above on device before changing anything.

Drag stop handler (shared by both card types):

```kotlin
    fun dropped() {
        val key = draggedKey ?: return
        draggedKey = null
        vm.onDrop(rows, key)
    }
```

FAB (replace `onClick`):

```kotlin
            Box {
                FloatingActionButton(
                    onClick = { fabMenu = true },
                    shape = RoundedCornerShape(topStart = 26.dp, topEnd = 14.dp, bottomEnd = 26.dp, bottomStart = 14.dp),
                    containerColor = Primary,
                    contentColor = OnPrimary
                ) { Icon(Icons.Default.Add, contentDescription = "New") }
                DropdownMenu(expanded = fabMenu, onDismissRequest = { fabMenu = false }) {
                    DropdownMenuItem(text = { Text("New macro") },
                        onClick = { fabMenu = false; editorMacroId = null; showEditor = true })
                    DropdownMenuItem(text = { Text("New folder") },
                        onClick = { fabMenu = false; showCreateFolder = true })
                }
            }
```

Grid body — replace `items(visible, …) { macro -> … }`: in search mode keep today's flat macro rendering (from `visible`); otherwise iterate rows:

```kotlin
                    items(rows, key = { it.key }) { row ->
                        ReorderableItem(reorderState, key = row.key) { _ ->
                            when (row) {
                                is FolderRow -> FolderCard(
                                    folder = row.folder,
                                    memberCount = row.memberCount,
                                    activeCount = row.activeCount,
                                    switchOn = folderSwitchOn(row.memberCount, row.activeCount),
                                    onClick = { vm.onToggleExpanded(row.folder) },
                                    onToggleAll = { enabled -> vm.onToggleFolder(row.folder, enabled) },
                                    onRename = { renameTarget = row.folder },
                                    onDelete = { deleteTarget = row.folder },
                                    dragHandleModifier = Modifier.draggableHandle(onDragStopped = { dropped() })
                                )
                                is MacroRow -> {
                                    val member = row.macro.folderId != null
                                    val folderColor = folders.firstOrNull { it.id == row.macro.folderId }
                                        ?.let { Color(it.cardColor) }
                                    MacroCard(
                                        macro = row.macro,
                                        onClick = { editorMacroId = row.macro.id; showEditor = true },
                                        onDelete = { deleteMacro(row.macro) },
                                        onCopy = { vm.onCopy(row.macro) },
                                        onSend = { sendMacro(row.macro) },
                                        onToggle = { enabled -> vm.onToggle(row.macro, enabled) },
                                        veinColor = folderColor,
                                        onMoveToFolder = if (folders.isNotEmpty()) {
                                            { moveTarget = row.macro }
                                        } else null,
                                        onMoveToRoot = if (member) {
                                            { vm.onMoveToFolder(row.macro, null) }
                                        } else null,
                                        modifier = if (member) Modifier.padding(start = 16.dp) else Modifier,
                                        dragHandleModifier = Modifier.draggableHandle(onDragStopped = { dropped() })
                                    )
                                }
                            }
                        }
                    }
```

(Search-mode `items(visible, …)` block keeps the OLD MacroCard call without the new params; searching still disables drag by passing plain `Modifier` as before. Empty-state checks switch from `ordered` to `rows`; delete the now-dead `ordered` state and the `vm.onReorder` no-op from Task 4.)

Dialogs at the bottom of the composable (complete):

```kotlin
    if (showCreateFolder) FolderNameDialog(
        title = "New folder", initial = "",
        onConfirm = { vm.onCreateFolder(it); showCreateFolder = false },
        onDismiss = { showCreateFolder = false })

    renameTarget?.let { folder ->
        FolderNameDialog(
            title = "Rename folder", initial = folder.name,
            onConfirm = { vm.onRenameFolder(folder, it); renameTarget = null },
            onDismiss = { renameTarget = null })
    }

    deleteTarget?.let { folder ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete \"${folder.name}\"?") },
            text = { Text("The macros move out to the root — nothing is deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    scope.launch {
                        val members = vm.deleteFolder(folder)
                        val result = snackbar.showSnackbar("\"${folder.name}\" deleted", actionLabel = "Undo")
                        if (result == SnackbarResult.ActionPerformed) vm.onUndoDeleteFolder(folder, members)
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }

    moveTarget?.let { macro ->
        AlertDialog(
            onDismissRequest = { moveTarget = null },
            title = { Text("Move \"${macro.name}\" to…") },
            text = {
                Column {
                    folders.forEach { folder ->
                        TextButton(onClick = { vm.onMoveToFolder(macro, folder.id); moveTarget = null }) {
                            Text(folder.name)
                        }
                    }
                    TextButton(onClick = { vm.onMoveToFolder(macro, null); moveTarget = null }) {
                        Text("Root (no folder)")
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { moveTarget = null }) { Text("Cancel") } }
        )
    }
```

And the shared name dialog (bottom of the file):

```kotlin
@Composable
private fun FolderNameDialog(
    title: String, initial: String,
    onConfirm: (String) -> Unit, onDismiss: () -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Name") }, singleLine = true
            )
        },
        confirmButton = {
            Button(enabled = name.isNotBlank(), onClick = { onConfirm(name) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
```

Imports to add in `MacroListScreen.kt`: `Folder`, `FolderRow`, `MacroRow`, `ListRow`, `hideMembers`, `folderSwitchOn`, `FolderCard`, `DropdownMenu`, `DropdownMenuItem`, `androidx.compose.ui.graphics.Color` (already), `padding` (already via layout.*).

- [ ] **Step 4: Compile + full suite**

Run: `./gradlew compileDebugKotlin test --console=plain`
Expected: BUILD SUCCESSFUL, 117 tests. Also remove the deprecated `onReorder` no-op now (verify with `grep -rn "onReorder" app/src/` → no hits).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(folders): folder-aware list UI — FolderCard, drag-in, FAB menu, move dialogs

Co-Authored-By: WOZCODE <contact@withwoz.com>"
```

---

### Task 6: On-device verification, docs, push

**Files:**
- Modify: `HANDOVER.md` (fase 7 section), `README.md` (features row, DB table, layout)

- [ ] **Step 1: Build and install**

Run: `./gradlew assembleDebug --console=plain` → BUILD SUCCESSFUL.
Run: `adb devices` — if empty, ask the user (Danish) to plug the phone in; then:
`adb install -r app/build/outputs/apk/debug/app-debug.apk` → Success. Launch the app.

- [ ] **Step 2: Manual checklist (user confirms on device, in Danish)**

1. FAB → "New folder" → name it → folder card appears at top, expanded, "Empty folder — drag macros in".
2. Drag a macro under the folder header → it indents and its vein takes the folder color; kill + reopen the app → membership and collapse state survive.
3. Tap folder → collapses (chevron rotates, members hidden); tap again → expands.
4. Folder switch: partial state shows OFF; tap → all members enabled (check a scheduled member's alarm still fires later / Health screen shows next fire).
5. Long-press member → "Move to root" → outdents. Long-press root macro → "Move to folder…" → picker works.
6. Long-press folder → Rename works; Delete → members drop to root; snackbar Undo restores membership.
7. Settings → Export backup → file contains `"folders"`; wipe-free re-import → folders and membership intact.
8. Search still shows a flat list, no folders, no drag.

- [ ] **Step 3: Update docs**

`HANDOVER.md`: add a "fase 7 — mapper (2026-07-19)" section at the top mirroring the established format: DB v13, new files, drop rule + known corner, test count.
`README.md`: features table row `| 📁 | **Folders** — nameable accordion cards; drag macros in, master enable/disable switch, drag-out or menu-move back to root |`; DB section header to v13 + migration row `| 12 → 13 | folders table + folder_id on macros |`; project layout: add `FolderCard`, `FolderLayout`, `FolderDao/FolderRepository/Folder` mentions.

- [ ] **Step 4: Final suite + commit + push**

Run: `./gradlew test --console=plain` → 117 green.

```bash
git add -A
git commit -m "docs: folders in HANDOVER (fase 7) and README (v13)

Co-Authored-By: WOZCODE <contact@withwoz.com>"
git push
```
