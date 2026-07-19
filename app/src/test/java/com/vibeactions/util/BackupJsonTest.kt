package com.vibeactions.util

import com.vibeactions.domain.model.Folder
import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupJsonTest {
    private val macros = listOf(
        Macro("id-1", "Morning", TriggerType.SCHEDULED, "09:00", true, listOf("+4512345678"),
            "Hej", true, null, null, 100L),
        Macro("id-2", "Car", TriggerType.BLUETOOTH, null, true, listOf("+4587654321"),
            "På vej", true, null, null, 200L,
            triggerOnConnect = true, triggerTarget = "AA:BB:CC:DD:EE:FF",
            triggerTargetLabel = "Car stereo")
    )
    private val settings = mapOf(
        "gemini_system_prompt" to "Svar kort",
        "gemini_model" to "gemini-2.5-flash",
        "bg_hue" to "180.0",
        "quiet_hours_enabled" to "true"
    )

    @Test fun fullBackup_roundTrips() {
        val json = exportBackup(macros, emptyList(), settings)
        val parsed = parseBackup(json)
        assertEquals(macros, parsed.macros)
        assertEquals(settings, parsed.settings)
    }

    @Test fun stateTriggerFields_survive() {
        val parsed = parseBackup(exportBackup(macros, emptyList(), emptyMap()))
        val car = parsed.macros.first { it.id == "id-2" }
        assertEquals("AA:BB:CC:DD:EE:FF", car.triggerTarget)
        assertEquals("Car stereo", car.triggerTargetLabel)
        assertTrue(car.triggerOnConnect)
    }

    @Test fun legacyMacroArray_stillImports() {
        // A file produced by the old macro-only export (a bare JSON array) must still import,
        // yielding the macros and empty settings.
        val legacy = exportMacros(macros)
        assertTrue(legacy.trimStart().startsWith("["))
        val parsed = parseBackup(legacy)
        assertEquals(macros, parsed.macros)
        assertTrue(parsed.settings.isEmpty())
    }

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
}
