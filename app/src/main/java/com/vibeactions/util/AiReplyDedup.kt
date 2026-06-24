package com.vibeactions.util

import java.time.Instant
import java.time.ZoneId

/**
 * Stable per-day key identifying one AI auto-reply job, used to suppress a duplicate send when
 * WorkManager re-runs a worker that died mid-flight (the retry carries identical inputData, so the
 * key is identical). The sender is digit-normalised to match the receiver's throttle key, and the
 * key is scoped to the local calendar day so an identical message on a later day still goes through.
 */
fun aiReplyDedupKey(
    macroId: String,
    sender: String,
    body: String,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault()
): String {
    val digits = sender.filter { it.isDigit() }
    val day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay()
    return "$macroId|$digits|$day|${body.hashCode()}"
}
