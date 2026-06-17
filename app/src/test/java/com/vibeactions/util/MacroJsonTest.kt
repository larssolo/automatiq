package com.vibeactions.util

import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.MacroStatus
import com.vibeactions.domain.model.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Test

class MacroJsonTest {
    @Test fun roundTripPreservesAllFields() {
        val macros = listOf(
            Macro("id-1", "Morning", TriggerType.SCHEDULED, "09:00", true, "+4512345678",
                "Hej", true, 123L, MacroStatus.SUCCESS, 100L,
                lastScheduledFireAt = 124L, sortOrder = 2, daysOfWeek = setOf(1, 3, 5),
                weekInterval = 2, anchorEpochDay = 20620L),
            Macro("id-2", "Tap", TriggerType.MANUAL, null, true, "+4587654321",
                "Yo", false, null, null, 200L,
                lastScheduledFireAt = null, sortOrder = 0, daysOfWeek = setOf(1, 2, 3, 4, 5, 6, 7),
                weekInterval = 1, anchorEpochDay = null)
        )
        val json = exportMacros(macros)
        val restored = importMacros(json)
        assertEquals(macros, restored)
    }
}
