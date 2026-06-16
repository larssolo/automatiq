package com.vibeactions.domain

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
}
