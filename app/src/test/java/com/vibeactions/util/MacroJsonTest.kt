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
                "Hej", true, 123L, MacroStatus.SUCCESS, 100L),
            Macro("id-2", "Tap", TriggerType.MANUAL, null, true, "+4587654321",
                "Yo", false, null, null, 200L)
        )
        val json = exportMacros(macros)
        val restored = importMacros(json)
        assertEquals(macros, restored)
    }
}
