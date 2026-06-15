package com.vibeactions.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TimeUtilsTest {
    private val zone = ZoneId.of("Europe/Copenhagen")

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
}
