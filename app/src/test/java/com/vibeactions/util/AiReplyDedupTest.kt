package com.vibeactions.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class AiReplyDedupTest {
    private val zone = ZoneId.of("Europe/Copenhagen")
    private fun at(y: Int, mo: Int, d: Int, h: Int) =
        LocalDateTime.of(y, mo, d, h, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun sameInputsSameDay_sameKey() {
        // A WorkManager retry re-runs with identical inputData, so the key must be stable.
        val a = aiReplyDedupKey("m1", "+4512345678", "Hej", at(2026, 6, 15, 9), zone)
        val b = aiReplyDedupKey("m1", "+4512345678", "Hej", at(2026, 6, 15, 9), zone)
        assertEquals(a, b)
    }

    @Test fun senderNormalisedToDigits_sameKey() {
        val a = aiReplyDedupKey("m1", "+45 12 34 56 78", "Hej", at(2026, 6, 15, 9), zone)
        val b = aiReplyDedupKey("m1", "4512345678", "Hej", at(2026, 6, 15, 9), zone)
        assertEquals(a, b)
    }

    @Test fun differentBody_differentKey() {
        val a = aiReplyDedupKey("m1", "+4512345678", "Hej", at(2026, 6, 15, 9), zone)
        val b = aiReplyDedupKey("m1", "+4512345678", "Davs", at(2026, 6, 15, 9), zone)
        assertNotEquals(a, b)
    }

    @Test fun sameInputsDifferentDay_differentKey() {
        // Scoped per day so an identical message next week is not wrongly suppressed.
        val a = aiReplyDedupKey("m1", "+4512345678", "Hej", at(2026, 6, 15, 9), zone)
        val b = aiReplyDedupKey("m1", "+4512345678", "Hej", at(2026, 6, 16, 9), zone)
        assertNotEquals(a, b)
    }
}
