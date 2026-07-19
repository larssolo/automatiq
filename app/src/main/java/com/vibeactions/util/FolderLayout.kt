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
        // Only inherit the row-above's folder when it names a REAL folder present in the list;
        // an orphan macro (folderId → deleted folder, shown at root) must not pass on its ghost id.
        above is MacroRow && above.macro.folderId != null &&
            rows.any { it is FolderRow && it.folder.id == above.macro.folderId } -> above.macro.folderId
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
