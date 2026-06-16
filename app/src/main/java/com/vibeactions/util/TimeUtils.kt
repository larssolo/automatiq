package com.vibeactions.util

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** ISO day numbers 1=Monday … 7=Sunday. */
val ALL_DAYS: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7)
private val DAY_ABBR = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/**
 * Next epoch-ms at which [hhmm] ("HH:mm") occurs at or after [now] on one of the allowed weekdays
 * [days] (ISO 1=Mon..7=Sun). If today is allowed and the time hasn't passed, that's today;
 * otherwise the soonest later allowed weekday. An empty [days] is treated as every day.
 */
fun calculateNextFireTime(
    hhmm: String,
    now: LocalDateTime = LocalDateTime.now(),
    zone: ZoneId = ZoneId.systemDefault(),
    days: Set<Int> = ALL_DAYS
): Long {
    val target = LocalTime.parse(hhmm)
    val allowed = if (days.isEmpty()) ALL_DAYS else days
    val date = now.toLocalDate()
    for (i in 0..7) {
        val candidate = date.plusDays(i.toLong()).atTime(target)
        if (candidate.isAfter(now) && candidate.dayOfWeek.value in allowed) {
            return candidate.atZone(zone).toInstant().toEpochMilli()
        }
    }
    // Unreachable for a non-empty allowed set; fall back to tomorrow.
    return date.plusDays(1).atTime(target).atZone(zone).toInstant().toEpochMilli()
}

fun formatTime(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

/** Human label for a weekday set: "Every day", "Weekdays", "Weekend", or e.g. "Mon · Wed · Fri". */
fun formatDays(days: Set<Int>): String {
    val d = if (days.isEmpty()) ALL_DAYS else days
    return when {
        d.size == 7 -> "Every day"
        d == setOf(1, 2, 3, 4, 5) -> "Weekdays"
        d == setOf(6, 7) -> "Weekend"
        else -> d.sorted().joinToString(" · ") { DAY_ABBR[it - 1] }
    }
}
