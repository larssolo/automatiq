package com.vibeactions.domain

import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.TriggerType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class IdempotencyGuardTest {
    private val zone = ZoneId.of("Europe/Copenhagen")
    private val now = LocalDateTime.of(2026, 6, 15, 9, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun neverSent_isNotSentToday() {
        assertFalse(alreadySentToday(lastTriggeredAt = null, now = now, zone = zone))
    }
    @Test fun sentEarlierToday_isSentToday() {
        val earlier = LocalDateTime.of(2026, 6, 15, 8, 59).atZone(zone).toInstant().toEpochMilli()
        assertTrue(alreadySentToday(earlier, now, zone))
    }
    @Test fun sentYesterday_isNotSentToday() {
        val yest = LocalDateTime.of(2026, 6, 14, 9, 0).atZone(zone).toInstant().toEpochMilli()
        assertFalse(alreadySentToday(yest, now, zone))
    }

    /**
     * Regression: a manual/widget tap earlier today sets lastTriggeredAt but NOT
     * lastScheduledFireAt. The scheduled fire guard reads lastScheduledFireAt, so the day's
     * scheduled send is still allowed even though the macro was triggered manually today.
     */
    @Test fun manualTapTodayDoesNotBlockScheduledFire() {
        val macro = Macro(
            id = "m1", name = "Morning", triggerType = TriggerType.SCHEDULED, scheduledTime = "09:00",
            recipients = listOf("+4512345678"), messageBody = "Hej",
            lastTriggeredAt = now,            // manual tap today
            lastScheduledFireAt = null        // scheduled occurrence has NOT fired today
        )
        // UI "last triggered" reflects the manual tap...
        assertTrue(alreadySentToday(macro.lastTriggeredAt, now, zone))
        // ...but the scheduled guard is NOT blocked.
        assertFalse(alreadySentToday(macro.lastScheduledFireAt, now, zone))
    }
}
