package com.vibeactions.util

import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.TriggerType
import org.junit.Assert.assertEquals
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
        val json = exportBackup(macros, settings)
        val parsed = parseBackup(json)
        assertEquals(macros, parsed.macros)
        assertEquals(settings, parsed.settings)
    }

    @Test fun stateTriggerFields_survive() {
        val parsed = parseBackup(exportBackup(macros, emptyMap()))
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
}
