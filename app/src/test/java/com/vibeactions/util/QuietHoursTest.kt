package com.vibeactions.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietHoursTest {
    // 22:00 -> 07:00 wraps past midnight.
    private val start = 22 * 60
    private val end = 7 * 60

    @Test fun insideWrappingWindow_lateEvening() {
        assertTrue(isWithinQuietHours(23 * 60, start, end))     // 23:00
        assertTrue(isWithinQuietHours(22 * 60, start, end))     // 22:00 (inclusive start)
    }

    @Test fun insideWrappingWindow_earlyMorning() {
        assertTrue(isWithinQuietHours(0, start, end))           // 00:00
        assertTrue(isWithinQuietHours(6 * 60 + 59, start, end)) // 06:59
    }

    @Test fun outsideWrappingWindow_daytime() {
        assertFalse(isWithinQuietHours(7 * 60, start, end))     // 07:00 (exclusive end)
        assertFalse(isWithinQuietHours(12 * 60, start, end))    // 12:00
        assertFalse(isWithinQuietHours(21 * 60 + 59, start, end)) // 21:59
    }

    @Test fun nonWrappingWindow() {
        // 09:00 -> 17:00 (a plain daytime window).
        assertTrue(isWithinQuietHours(10 * 60, 9 * 60, 17 * 60))
        assertFalse(isWithinQuietHours(8 * 60, 9 * 60, 17 * 60))
        assertFalse(isWithinQuietHours(17 * 60, 9 * 60, 17 * 60))
    }

    @Test fun emptyWindow_neverQuiet() {
        assertFalse(isWithinQuietHours(0, 0, 0))
        assertFalse(isWithinQuietHours(12 * 60, 8 * 60, 8 * 60))
    }

    @Test fun minutesUntilQuietEnd_insideWrappingWindow() {
        assertEquals(480, minutesUntilQuietEnd(23 * 60, start, end)) // 23:00 -> 07:00
        assertEquals(1, minutesUntilQuietEnd(6 * 60 + 59, start, end))
    }

    @Test fun minutesUntilQuietEnd_outsideWindowIsZero() {
        assertEquals(0, minutesUntilQuietEnd(12 * 60, start, end))
        assertEquals(0, minutesUntilQuietEnd(12 * 60, 0, 0)) // empty window
    }

    @Test fun minutesUntilQuietEnd_nonWrappingWindow() {
        assertEquals(420, minutesUntilQuietEnd(10 * 60, 9 * 60, 17 * 60))
    }

    @Test fun formatMinuteOfDay_pads() {
        assertEquals("07:00", formatMinuteOfDay(7 * 60))
        assertEquals("22:05", formatMinuteOfDay(22 * 60 + 5))
        assertEquals("00:00", formatMinuteOfDay(0))
        assertEquals("00:00", formatMinuteOfDay(1440)) // wraps
    }
}
