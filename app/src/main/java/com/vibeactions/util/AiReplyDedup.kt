package com.vibeactions.util

import java.time.Instant
import java.time.ZoneId

/**
 * Stable per-day key identifying one AI auto-reply job, used to suppress a duplicate send when
 * WorkManager re-runs a worker that died mid-flight (the retry carries identical inputData —
 * including [eventId] — so the key is identical). The eventId is minted per incoming SMS, so a
 * *new* identical message from the same sender later the same day gets a fresh key and is NOT
 * suppressed. The sender is digit-normalised to match the receiver's throttle key, and the key is
 * scoped to the local calendar day so the claim store can be pruned to today's keys.
 */
fun aiReplyDedupKey(
    macroId: String,
    sender: String,
    eventId: String,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault()
): String {
    val digits = sender.filter { it.isDigit() }
    val day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay()
    return "$macroId|$digits|$day|$eventId"
}
