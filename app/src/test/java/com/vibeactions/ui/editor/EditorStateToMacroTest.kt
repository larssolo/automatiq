package com.vibeactions.ui.editor

import com.vibeactions.domain.model.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EditorStateToMacroTest {

    @Test fun incoming_clearsStaleRecipients() {
        // Switching an existing macro's trigger to Auto-reply must not keep the old recipient
        // list: the failed-send Retry action fires via macro.recipients and would otherwise
        // resend the fixed body to numbers that no longer apply.
        val state = EditorState(
            name = "Auto", triggerType = TriggerType.INCOMING,
            recipients = listOf("+4512345678"), message = "Fallback"
        )
        assertTrue(state.toMacro("id-1").recipients.isEmpty())
    }

    @Test fun scheduled_keepsTrimmedNonBlankRecipients() {
        val state = EditorState(
            name = "Morning", triggerType = TriggerType.SCHEDULED,
            recipients = listOf(" +4512345678 ", "", "+4587654321"), message = "Hej"
        )
        assertEquals(listOf("+4512345678", "+4587654321"), state.toMacro("id-1").recipients)
    }

    @Test fun multiWeek_anchorsOnFirstAllowedDayOnOrAfterStart() {
        // Start Wed 2026-06-17 (epoch day 20621), allowed day Monday only, every 2 weeks:
        // the anchor must land on the first actual fire, Mon 2026-06-22.
        val state = EditorState(
            name = "Biweekly", triggerType = TriggerType.SCHEDULED,
            recipients = listOf("+4512345678"), message = "Hej",
            daysOfWeek = setOf(1), weekInterval = 2,
            startEpochDay = LocalDate.of(2026, 6, 17).toEpochDay()
        )
        assertEquals(LocalDate.of(2026, 6, 22).toEpochDay(), state.toMacro("id-1").anchorEpochDay)
    }

    @Test fun weeklySchedule_hasNoAnchor() {
        val state = EditorState(
            name = "Weekly", triggerType = TriggerType.SCHEDULED,
            recipients = listOf("+4512345678"), message = "Hej", weekInterval = 1
        )
        assertNull(state.toMacro("id-1").anchorEpochDay)
    }

    @Test fun missedCall_clearsRecipientsKeepsCallerFilterDropsKeywordAndAi() {
        val state = EditorState(
            name = "Callback", triggerType = TriggerType.MISSED_CALL,
            recipients = listOf("+4512345678"), message = "Jeg ringer tilbage",
            matchSender = "+45 87 65 43 21", matchKeyword = "hello", aiReplyEnabled = true
        )
        val macro = state.toMacro("id-1")
        assertTrue(macro.recipients.isEmpty())
        assertEquals("+45 87 65 43 21", macro.matchSender)
        assertNull(macro.matchKeyword)      // keyword needs a message body; calls have none
        assertEquals(false, macro.aiReplyEnabled)
    }

    @Test fun incoming_blankMatchFieldsBecomeNull() {
        val state = EditorState(
            name = "Auto", triggerType = TriggerType.INCOMING,
            message = "Fallback", matchSender = "  ", matchKeyword = ""
        )
        val macro = state.toMacro("id-1")
        assertNull(macro.matchSender)
        assertNull(macro.matchKeyword)
    }
}
