package com.vibeactions.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
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
}
