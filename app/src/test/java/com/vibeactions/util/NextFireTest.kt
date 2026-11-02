package com.vibeactions.util

import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.TriggerType
import com.vibeactions.domain.model.randomDays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class NextFireTest {
    private val zone = ZoneOffset.UTC
    // Monday 2 Nov 2026, 12:00.
    private val now = LocalDateTime.of(2026, 11, 2, 12, 0)

    private fun macro(
        time: String = "08:15",
        days: Set<Int> = ALL_DAYS,
        interval: Int = 1,
        anchor: Long? = null,
        until: Long? = null,
        randomMin: Int = 0,
        randomMax: Int = 0,
        spread: Int = 0,
        trigger: TriggerType = TriggerType.SCHEDULED
    ) = Macro(
        id = "m1", name = "Pippi", triggerType = trigger, scheduledTime = time,
        recipients = listOf("+4512345678"), messageBody = "Hi",
        daysOfWeek = days, weekInterval = interval, anchorEpochDay = anchor,
        validUntilEpochDay = until, randomDaysMin = randomMin, randomDaysMax = randomMax,
        randomSpreadMinutes = spread
    )

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime.of(y, mo, d, h, mi).toInstant(zone).toEpochMilli()

    @Test fun laterTodayFiresToday() {
        assertEquals(at(2026, 11, 2, 14, 55), macro(time = "14:55").nextFireAt(now, zone))
    }

    @Test fun passedTodayFiresTomorrow() {
        assertEquals(at(2026, 11, 3, 8, 15), macro().nextFireAt(now, zone))
    }

    @Test fun skipsUnselectedDays() {
        // Only Fridays.
        assertEquals(at(2026, 11, 6, 8, 15), macro(days = setOf(5)).nextFireAt(now, zone))
    }

    @Test fun everyOtherWeekSkipsTheOffWeek() {
        // Anchored on Monday 26 Oct: the week of 2 Nov is the off week, so next is Monday 9 Nov.
        val anchor = LocalDate.of(2026, 10, 26).toEpochDay()
        assertEquals(at(2026, 11, 9, 8, 15), macro(days = setOf(1), interval = 2, anchor = anchor).nextFireAt(now, zone))
    }

    @Test fun matchesTheAlarmWithRandomDaysAndSpread() {
        val m = macro(days = setOf(1, 2, 3, 4, 5), randomMin = 1, randomMax = 2, spread = 20)
        val alarm = calculateNextFireTime(
            "08:15", now = now, zone = zone, days = m.daysOfWeek, jitterSeed = m.id,
            jitterSpreadMinutes = 20, randomDays = m.randomDays()
        )
        assertEquals(alarm, m.nextFireAt(now, zone))
    }

    @Test fun endedMacroHasNoNextRun() {
        val yesterday = LocalDate.of(2026, 11, 1).toEpochDay()
        assertNull(macro(until = yesterday).nextFireAt(now, zone))
    }

    @Test fun nonScheduledHasNoNextRun() {
        assertNull(macro(trigger = TriggerType.MANUAL).nextFireAt(now, zone))
        assertNull(macro(time = "25:99").nextFireAt(now, zone))
    }

    @Test fun formatsRelativeToNow() {
        assertEquals("today 14:55", formatNextFire(at(2026, 11, 2, 14, 55), now, zone))
        assertEquals("tomorrow 08:15", formatNextFire(at(2026, 11, 3, 8, 15), now, zone))
        assertEquals("Fri 08:15", formatNextFire(at(2026, 11, 6, 8, 15), now, zone))
        assertEquals("Mon 9 Nov 08:15", formatNextFire(at(2026, 11, 9, 8, 15), now, zone))
    }
}
