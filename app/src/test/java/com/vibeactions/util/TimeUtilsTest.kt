package com.vibeactions.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class TimeUtilsTest {
    private val zone = ZoneId.of("Europe/Copenhagen")

    private fun fireAt(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime()

    @Test fun laterToday_returnsToday() {
        val now = LocalDateTime.of(2026, 6, 15, 8, 0)
        val fire = calculateNextFireTime("09:00", now, zone)
        assertEquals(LocalDateTime.of(2026, 6, 15, 9, 0).atZone(zone).toInstant().toEpochMilli(), fire)
    }

    @Test fun earlierToday_returnsTomorrow() {
        val now = LocalDateTime.of(2026, 6, 15, 10, 0)
        val fire = calculateNextFireTime("09:00", now, zone)
        assertEquals(LocalDateTime.of(2026, 6, 16, 9, 0).atZone(zone).toInstant().toEpochMilli(), fire)
    }

    @Test fun exactlyNow_returnsTomorrow() {
        val now = LocalDateTime.of(2026, 6, 15, 9, 0)
        val fire = calculateNextFireTime("09:00", now, zone)
        assertEquals(LocalDateTime.of(2026, 6, 16, 9, 0).atZone(zone).toInstant().toEpochMilli(), fire)
    }

    @Test fun formatTime_padsCorrectly() {
        assertEquals("09:05", formatTime(9, 5))
    }

    @Test fun weekday_todayAllowedAndNotPassed_returnsToday() {
        val now = LocalDateTime.of(2026, 6, 15, 8, 0)
        val fire = calculateNextFireTime("09:00", now, zone, days = setOf(now.dayOfWeek.value))
        assertEquals(now.toLocalDate().atTime(9, 0), fireAt(fire))
    }

    @Test fun weekday_onlyTodayAllowedButPassed_returnsSameWeekdayNextWeek() {
        val now = LocalDateTime.of(2026, 6, 15, 10, 0)
        val fire = calculateNextFireTime("09:00", now, zone, days = setOf(now.dayOfWeek.value))
        assertEquals(now.toLocalDate().plusDays(7).atTime(9, 0), fireAt(fire))
    }

    @Test fun weekday_todayExcluded_skipsToNextAllowedDay() {
        val now = LocalDateTime.of(2026, 6, 15, 8, 0)
        val tomorrow = now.toLocalDate().plusDays(1)
        val fire = calculateNextFireTime("09:00", now, zone, days = setOf(tomorrow.dayOfWeek.value))
        assertEquals(tomorrow.atTime(9, 0), fireAt(fire))
    }

    @Test fun weekday_emptySetTreatedAsEveryDay() {
        val now = LocalDateTime.of(2026, 6, 15, 8, 0)
        val fire = calculateNextFireTime("09:00", now, zone, days = emptySet())
        assertEquals(now.toLocalDate().atTime(9, 0), fireAt(fire))
    }

    @Test fun formatDays_labels() {
        assertEquals("Every day", formatDays(setOf(1, 2, 3, 4, 5, 6, 7)))
        assertEquals("Weekdays", formatDays(setOf(1, 2, 3, 4, 5)))
        assertEquals("Weekend", formatDays(setOf(6, 7)))
        assertEquals("Mon · Wed · Fri", formatDays(setOf(1, 3, 5)))
        assertTrue(formatDays(emptySet()) == "Every day")
    }

    @Test fun biweekly_firesAnchorWeekThenSkips() {
        val anchorMon = LocalDate.of(2026, 6, 15).with(DayOfWeek.MONDAY)
        val anchor = anchorMon.toEpochDay()
        val mondays = setOf(1)
        // Anchor Monday, before the time -> fires that Monday.
        val f0 = calculateNextFireTime("09:00", anchorMon.atTime(8, 0), zone,
            days = mondays, weekInterval = 2, anchorEpochDay = anchor)
        assertEquals(anchorMon.atTime(9, 0), fireAt(f0))
        // Anchor Monday, time passed -> next fire is 14 days later, NOT the in-between Monday.
        val f1 = calculateNextFireTime("09:00", anchorMon.atTime(10, 0), zone,
            days = mondays, weekInterval = 2, anchorEpochDay = anchor)
        assertEquals(anchorMon.plusDays(14).atTime(9, 0), fireAt(f1))
    }

    @Test fun isScheduledDay_biweeklyAlignment() {
        val anchorMon = LocalDate.of(2026, 6, 15).with(DayOfWeek.MONDAY)
        val anchor = anchorMon.toEpochDay()
        val mondays = setOf(1)
        assertTrue(isScheduledDay(anchorMon, mondays, 2, anchor))              // week 0
        assertFalse(isScheduledDay(anchorMon.plusDays(7), mondays, 2, anchor)) // week 1 (off)
        assertTrue(isScheduledDay(anchorMon.plusDays(14), mondays, 2, anchor)) // week 2
        assertFalse(isScheduledDay(anchorMon.minusDays(7), mondays, 2, anchor)) // before anchor
    }

    @Test fun intervalOne_behavesWeekly() {
        val now = LocalDateTime.of(2026, 6, 15, 10, 0)
        val weekly = calculateNextFireTime("09:00", now, zone, weekInterval = 1, anchorEpochDay = null)
        assertEquals(now.toLocalDate().plusDays(1).atTime(9, 0), fireAt(weekly))
    }

    @Test fun formatRecurrence_labels() {
        assertEquals("Every day", formatRecurrence(setOf(1, 2, 3, 4, 5, 6, 7), 1))
        assertEquals("Every 2 weeks · Mon", formatRecurrence(setOf(1), 2))
    }

    @Test fun validUntil_dayAfterExpiry_isNotScheduled() {
        val day = LocalDate.of(2026, 6, 15)
        // Expiry on the 15th: the 15th still fires, the 16th does not.
        assertTrue(isScheduledDay(day, ALL_DAYS, validUntilEpochDay = day.toEpochDay()))
        assertFalse(isScheduledDay(day.plusDays(1), ALL_DAYS, validUntilEpochDay = day.toEpochDay()))
    }

    @Test fun futureAnchor_neverFiresBeforeStartDate() {
        // Biweekly Monday macro whose start date is months away: the next fire must be the anchor
        // Monday itself — not the "tomorrow" fallback that made pre-start macros fire daily.
        val now = LocalDateTime.of(2026, 6, 15, 8, 0) // a Monday
        val anchorMon = LocalDate.of(2026, 9, 14)     // a Monday, 13 weeks ahead
        val fire = calculateNextFireTime("09:00", now, zone,
            days = setOf(1), weekInterval = 2, anchorEpochDay = anchorMon.toEpochDay())
        assertEquals(anchorMon.atTime(9, 0), fireAt(fire))
    }

    @Test fun futureAnchor_weeklyAlsoStartsAtAnchor() {
        val now = LocalDateTime.of(2026, 6, 15, 8, 0)
        val anchor = LocalDate.of(2026, 7, 20) // a Monday, 5 weeks ahead
        val fire = calculateNextFireTime("09:00", now, zone,
            days = setOf(1), weekInterval = 2, anchorEpochDay = anchor.toEpochDay())
        assertEquals(anchor.atTime(9, 0), fireAt(fire))
    }

    @Test fun newMacro_timeAlreadyPassedToday_consumesTodaysFire() {
        val now = LocalDateTime.of(2026, 6, 15, 10, 0)
        val stamp = consumedFireStampForNewMacro("09:00", ALL_DAYS, now = now, zone = zone)
        assertEquals(now.atZone(zone).toInstant().toEpochMilli(), stamp)
    }

    @Test fun newMacro_timeStillAheadToday_noStamp() {
        val now = LocalDateTime.of(2026, 6, 15, 8, 0)
        assertEquals(null, consumedFireStampForNewMacro("09:00", ALL_DAYS, now = now, zone = zone))
    }

    @Test fun newMacro_todayNotAScheduledDay_noStamp() {
        val now = LocalDateTime.of(2026, 6, 15, 10, 0) // a Monday
        assertEquals(null, consumedFireStampForNewMacro("09:00", setOf(2), now = now, zone = zone))
    }

    @Test fun parseHhMmOrNull_acceptsValidRejectsGarbage() {
        assertEquals(java.time.LocalTime.of(9, 5), parseHhMmOrNull("09:05"))
        assertEquals(null, parseHhMmOrNull("9 o'clock"))
        assertEquals(null, parseHhMmOrNull(""))
        assertEquals(null, parseHhMmOrNull("25:00"))
    }

    @Test fun validUntil_pastExpiry_nextFireFallsBackBeyondExpiry() {
        // After expiry the search finds no valid day; AlarmScheduler guards against arming this.
        val now = LocalDateTime.of(2026, 6, 20, 8, 0)
        val expired = LocalDate.of(2026, 6, 15).toEpochDay()
        val fire = calculateNextFireTime("09:00", now, zone, validUntilEpochDay = expired)
        // Fallback is tomorrow (past expiry) — caller must reject it.
        assertTrue(fireAt(fire).toLocalDate().toEpochDay() > expired)
    }
}
