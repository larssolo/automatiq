package com.vibeactions.util

import com.vibeactions.domain.model.AiSendMode
import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.MacroStatus
import com.vibeactions.domain.model.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Test

class MacroJsonTest {
    @Test fun roundTripPreservesAllFields() {
        val macros = listOf(
            Macro("id-1", "Morning", TriggerType.SCHEDULED, "09:00", true, listOf("+4512345678", "+4500000000"),
                "Hej", true, 123L, MacroStatus.SUCCESS, 100L,
                lastScheduledFireAt = 124L, sortOrder = 2, daysOfWeek = setOf(1, 3, 5),
                weekInterval = 2, anchorEpochDay = 20620L, cardColor = 0xFFEF9A9AL),
            Macro("id-2", "Tap", TriggerType.MANUAL, null, true, listOf("+4587654321"),
                "Yo", false, null, null, 200L,
                lastScheduledFireAt = null, sortOrder = 0, daysOfWeek = setOf(1, 2, 3, 4, 5, 6, 7),
                weekInterval = 1, anchorEpochDay = null),
            Macro("id-3", "Auto", TriggerType.INCOMING, null, true, emptyList(),
                "Fallback", true, null, null, 300L,
                lastScheduledFireAt = null, sortOrder = 1, daysOfWeek = setOf(1, 2, 3, 4, 5, 6, 7),
                weekInterval = 1, anchorEpochDay = null,
                matchSender = "+4520836358", matchKeyword = "godnat",
                aiReplyEnabled = true, aiSendMode = AiSendMode.AUTO,
                aiReplyInstruction = "Svar kort og venligt på dansk, maks. 1 sætning")
        )
        val json = exportMacros(macros)
        val restored = importMacros(json)
        assertEquals(macros, restored)
    }
}
