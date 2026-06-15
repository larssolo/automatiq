package com.vibeactions.domain

import java.time.Instant
import java.time.ZoneId

/** True if [lastTriggeredAt] falls on the same calendar day (in [zone]) as [now]. */
fun alreadySentToday(
    lastTriggeredAt: Long?,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault()
): Boolean {
    if (lastTriggeredAt == null) return false
    val last = Instant.ofEpochMilli(lastTriggeredAt).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return last == today
}
