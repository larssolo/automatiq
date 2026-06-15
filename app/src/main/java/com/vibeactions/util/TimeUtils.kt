package com.vibeactions.util

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Next epoch-ms at which [hhmm] ("HH:mm") occurs at or after [now]; if it has passed today, tomorrow. */
fun calculateNextFireTime(
    hhmm: String,
    now: LocalDateTime = LocalDateTime.now(),
    zone: ZoneId = ZoneId.systemDefault()
): Long {
    val target = LocalTime.parse(hhmm)
    var candidate = now.toLocalDate().atTime(target)
    if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
    return candidate.atZone(zone).toInstant().toEpochMilli()
}

fun formatTime(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)
