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

    @Test fun scheduled_keepsAiVariationFields() {
        // Scheduled macros can send AI-written variations: the AI fields must survive the save.
        val state = EditorState(
            name = "Morning", triggerType = TriggerType.SCHEDULED,
            recipients = listOf("+4512345678"), message = "Godmorgen!",
            aiReplyEnabled = true, aiSendMode = com.vibeactions.domain.model.AiSendMode.AUTO,
            aiReplyInstruction = "  varm og kort  "
        )
        val macro = state.toMacro("id-1")
        assertTrue(macro.aiReplyEnabled)
        assertEquals(com.vibeactions.domain.model.AiSendMode.AUTO, macro.aiSendMode)
        assertEquals("varm og kort", macro.aiReplyInstruction)
    }

    @Test fun scheduled_aiDisabled_dropsInstruction() {
        val state = EditorState(
            name = "Morning", triggerType = TriggerType.SCHEDULED,
            recipients = listOf("+4512345678"), message = "Godmorgen!",
            aiReplyEnabled = false, aiReplyInstruction = "varm og kort"
        )
        val macro = state.toMacro("id-1")
        assertEquals(false, macro.aiReplyEnabled)
        assertNull(macro.aiReplyInstruction)
    }

    @Test fun manual_dropsAiVariationFields() {
        // Only auto-reply and scheduled macros carry AI fields; a trigger-type switch to MANUAL
        // must not leave a stale enabled flag that would surprise-vary a plain manual send.
        val state = EditorState(
            name = "Tap", triggerType = TriggerType.MANUAL,
            recipients = listOf("+4512345678"), message = "Hej",
            aiReplyEnabled = true, aiReplyInstruction = "varm"
        )
        val macro = state.toMacro("id-1")
        assertEquals(false, macro.aiReplyEnabled)
        assertNull(macro.aiReplyInstruction)
    }

    @Test fun folderMembership_survivesEditRoundTrip() {
        // Editing a macro that lives in a folder must not kick it out on save.
        val state = EditorState(
            name = "Member", triggerType = TriggerType.SCHEDULED,
            recipients = listOf("+4512345678"), message = "Hej",
            folderId = "folder-1"
        )
        assertEquals("folder-1", state.toMacro("id-1").folderId)
    }

    @Test fun oneOff_mapsToSingleDayAnchorExpiryAndNoRepeat() {
        // A one-off on Thu 2026-12-24 must become: repeatDaily=false, that single weekday,
        // anchored on and expiring on the date — so the recurring engine fires it exactly once.
        val date = LocalDate.of(2026, 12, 24) // a Thursday (ISO day 4)
        val state = EditorState(
            name = "Julehilsen", triggerType = TriggerType.SCHEDULED,
            recipients = listOf("+4512345678"), message = "God jul",
            scheduledTime = "10:00", oneOff = true, oneOffEpochDay = date.toEpochDay()
        )
        val macro = state.toMacro("id-1")
        assertEquals(false, macro.repeatDaily)
        assertEquals(setOf(4), macro.daysOfWeek)
        assertEquals(date.toEpochDay(), macro.anchorEpochDay)
        assertEquals(date.toEpochDay(), macro.validUntilEpochDay)
        assertEquals(1, macro.weekInterval)
    }

    @Test fun recurring_staysRepeating() {
        val state = EditorState(
            name = "Daily", triggerType = TriggerType.SCHEDULED,
            recipients = listOf("+4512345678"), message = "Hej", oneOff = false
        )
        assertTrue(state.toMacro("id-1").repeatDaily)
    }

    @Test fun oneOff_canSaveWithNoWeekdaySelected() {
        // A one-off has no weekday picker, so an empty daysOfWeek must NOT block saving (the
        // weekday rule only applies to recurring macros).
        val state = EditorState(
            name = "Julehilsen", triggerType = TriggerType.SCHEDULED,
            recipients = listOf("+4512345678"), message = "God jul",
            oneOff = true, oneOffEpochDay = LocalDate.of(2026, 12, 24).toEpochDay(),
            daysOfWeek = emptySet()
        )
        assertTrue(state.daysValid)
        assertTrue(state.canSave)
    }

    @Test fun recurring_withNoWeekdayCannotSave() {
        val state = EditorState(
            name = "Broken", triggerType = TriggerType.SCHEDULED,
            recipients = listOf("+4512345678"), message = "Hej",
            oneOff = false, daysOfWeek = emptySet()
        )
        assertTrue(!state.canSave)
    }
}
