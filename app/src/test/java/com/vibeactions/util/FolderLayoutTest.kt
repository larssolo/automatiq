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
            macros = listOf(macro("root1", 0), macro("root2", 1), macro("root3", 2))
        ) // m:root1, m:root2, m:root3 → drag root1 between root2 and root3
        val moved = listOf(rows[1], rows[0], rows[2]) // m:root2, m:root1, m:root3
        val result = resolveDrop(moved, "m:root1")
        assertNull(result.movedMacroFolderId) // root1 lands below root2 (a root macro) → stays root
        assertNull((result.rows[1] as MacroRow).macro.folderId)
    }

    @Test fun drop_draggedFolder_neverChangesMembership() {
        val rows = flattenRows(
            folders = listOf(folder("A", 0, expanded = false), folder("B", 1, expanded = false)),
            macros = listOf(macro("a1", 0, "A"))
        )
        val result = resolveDrop(listOf(rows[1], rows[0]), "f:A")
        assertNull(result.movedMacroFolderId)
    }

    @Test fun drop_unknownDraggedKey_isNoOp() {
        val rows = flattenRows(
            folders = listOf(folder("A", 0)),
            macros = listOf(macro("m1", 0, "A"), macro("root1", 1))
        ) // f:A, m:m1, m:root1
        val result = resolveDrop(rows, "m:ghost")
        assertNull(result.movedMacroFolderId)
        assertEquals(rows, result.rows)
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

    @Test fun resolveDrop_belowOrphanMacro_landsAtRootNotGhostFolder() {
        // The row above the dropped macro is an orphan (folderId → a folder that isn't in the
        // list). The dragged macro must land at root, not inherit the ghost folder id.
        val rows = listOf<ListRow>(
            MacroRow(macro("orphan", sort = 0, folderId = "ghost")),
            MacroRow(macro("dragged", sort = 1, folderId = null))
        )
        val result = resolveDrop(rows, "m:dragged")
        assertNull(result.movedMacroFolderId)
    }

    @Test fun resolveDrop_belowRealFolderMember_joinsThatFolder() {
        val rows = listOf<ListRow>(
            FolderRow(folder("A", sort = 0), memberCount = 1, activeCount = 1),
            MacroRow(macro("m1", sort = 0, folderId = "A")),
            MacroRow(macro("dragged", sort = 1, folderId = null))
        )
        val result = resolveDrop(rows, "m:dragged")
        assertEquals("A", result.movedMacroFolderId)
    }

    // ── folderSwitchOn ──

    @Test fun switch_onOnlyWhenAllMembersEnabled() {
        assertTrue(folderSwitchOn(memberCount = 2, activeCount = 2))
        assertFalse(folderSwitchOn(memberCount = 2, activeCount = 1))   // partial shows OFF
        assertFalse(folderSwitchOn(memberCount = 0, activeCount = 0))   // empty folder
    }
}
