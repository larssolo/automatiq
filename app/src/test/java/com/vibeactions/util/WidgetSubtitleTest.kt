package com.vibeactions.util

import com.vibeactions.domain.model.MacroStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class WidgetSubtitleTest {
    private val zone = ZoneId.of("Europe/Copenhagen")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    private val now = at(2026, 7, 18, 20, 0)

    @Test fun neverFired_promptsToTap() {
        assertEquals("Tap to send", widgetSubtitle(null, null, now, zone))
    }

    @Test fun firedToday_showsTimeAndOutcomeMark() {
        assertEquals("Last: 14:32 ✓", widgetSubtitle(at(2026, 7, 18, 14, 32), MacroStatus.SUCCESS, now, zone))
        assertEquals("Last: 14:32 ✗", widgetSubtitle(at(2026, 7, 18, 14, 32), MacroStatus.FAILED, now, zone))
    }

    @Test fun firedEarlierDay_showsShortDate() {
        assertEquals("Last: 12/7 ✓", widgetSubtitle(at(2026, 7, 12, 9, 0), MacroStatus.SUCCESS, now, zone))
    }

    @Test fun pendingOutcome_hasNoMark() {
        assertEquals("Last: 19:59", widgetSubtitle(at(2026, 7, 18, 19, 59), MacroStatus.PENDING, now, zone))
    }
}
