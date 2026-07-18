package com.vibeactions.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** ISO day numbers 1=Monday … 7=Sunday. */
val ALL_DAYS: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7)
private val DAY_ABBR = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/**
 * Whether [date] is an active scheduled day for a macro: its weekday is in [days] (empty = all),
 * it is on/after the anchor, and — for a multi-week interval — its Monday-based week aligns with
 * the anchor week modulo [weekInterval]. Interval/anchor only apply when [weekInterval] > 1 and an
 * anchor is set; otherwise every matching weekday qualifies (unchanged weekly behaviour).
 */
fun isScheduledDay(
    date: LocalDate,
    days: Set<Int>,
    weekInterval: Int = 1,
    anchorEpochDay: Long? = null,
    validUntilEpochDay: Long? = null
): Boolean {
    if (validUntilEpochDay != null && date.toEpochDay() > validUntilEpochDay) return false
    val allowed = if (days.isEmpty()) ALL_DAYS else days
    if (date.dayOfWeek.value !in allowed) return false
    val anchor = anchorEpochDay?.let { LocalDate.ofEpochDay(it) }
    if (anchor != null && date.isBefore(anchor)) return false
    val interval = weekInterval.coerceAtLeast(1)
    if (interval > 1 && anchor != null) {
        val weeks = ChronoUnit.WEEKS.between(anchor.with(DayOfWeek.MONDAY), date.with(DayOfWeek.MONDAY))
        if (Math.floorMod(weeks, interval.toLong()) != 0L) return false
    }
    return true
}

/**
 * Next epoch-ms at which [hhmm] ("HH:mm") occurs at or after [now] on a day that satisfies
 * [isScheduledDay] for [days]/[weekInterval]/[anchorEpochDay].
 */
fun calculateNextFireTime(
    hhmm: String,
    now: LocalDateTime = LocalDateTime.now(),
    zone: ZoneId = ZoneId.systemDefault(),
    days: Set<Int> = ALL_DAYS,
    weekInterval: Int = 1,
    anchorEpochDay: Long? = null,
    validUntilEpochDay: Long? = null
): Long {
    val target = LocalTime.parse(hhmm)
    val horizon = weekInterval.coerceAtLeast(1) * 7 + 7
    val date = now.toLocalDate()
    // A future anchor (start date) means nothing may fire until then. Scan from the anchor in that
    // case: scanning from today would find no valid day inside the horizon and hit the tomorrow
    // fallback below — arming (and firing) the macro daily before its start date.
    val anchor = anchorEpochDay?.let { LocalDate.ofEpochDay(it) }
    val scanStart = if (anchor != null && anchor.isAfter(date)) anchor else date
    for (i in 0..horizon) {
        val d = scanStart.plusDays(i.toLong())
        val candidate = d.atTime(target)
        if (candidate.isAfter(now) &&
            isScheduledDay(d, days, weekInterval, anchorEpochDay, validUntilEpochDay)) {
            return candidate.atZone(zone).toInstant().toEpochMilli()
        }
    }
    // Only reachable when the macro's expiry has passed; callers must reject a fire past expiry.
    return date.plusDays(1).atTime(target).atZone(zone).toInstant().toEpochMilli()
}

/** Parses "HH:mm" leniently, returning null instead of throwing on malformed input (e.g. a
 *  hand-edited import file). Callers skip scheduling rather than crash — see importMacros. */
fun parseHhMmOrNull(hhmm: String): LocalTime? = runCatching { LocalTime.parse(hhmm) }.getOrNull()

/**
 * For a macro being created (or duplicated) right now: an epoch-ms stamp that consumes today's
 * scheduled fire when today's occurrence has already passed, else null. Without this the hourly
 * catch-up worker treats the pre-creation occurrence as "missed" and sends immediately — but the
 * macro didn't exist at its fire time, so there was nothing to miss. The alarm path agrees: it
 * arms the NEXT occurrence after now.
 */
fun consumedFireStampForNewMacro(
    hhmm: String?,
    days: Set<Int>,
    weekInterval: Int = 1,
    anchorEpochDay: Long? = null,
    validUntilEpochDay: Long? = null,
    now: LocalDateTime = LocalDateTime.now(),
    zone: ZoneId = ZoneId.systemDefault()
): Long? {
    val time = hhmm?.let { parseHhMmOrNull(it) } ?: return null
    val today = now.toLocalDate()
    val passedToday = !now.toLocalTime().isBefore(time)
    return if (passedToday &&
        isScheduledDay(today, days, weekInterval, anchorEpochDay, validUntilEpochDay))
        now.atZone(zone).toInstant().toEpochMilli() else null
}

/** First allowed weekday on or after [date] (ignores interval) — used to anchor a recurrence. */
fun firstScheduledDateOnOrAfter(date: LocalDate, days: Set<Int>): LocalDate {
    val allowed = if (days.isEmpty()) ALL_DAYS else days
    var d = date
    repeat(7) {
        if (d.dayOfWeek.value in allowed) return d
        d = d.plusDays(1)
    }
    return date
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

/** Recurrence label combining interval and days, e.g. "Every 2 weeks · Mon" or just "Weekdays". */
fun formatRecurrence(days: Set<Int>, weekInterval: Int): String =
    if (weekInterval <= 1) formatDays(days) else "Every $weekInterval weeks · ${formatDays(days)}"
