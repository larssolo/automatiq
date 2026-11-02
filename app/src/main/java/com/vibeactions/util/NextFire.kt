package com.vibeactions.util

import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.TriggerType
import com.vibeactions.domain.model.randomDays
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * When a scheduled macro runs next, in epoch ms — the one calculation the alarm, the health check
 * and the "Next" labels all share, so what the user sees is exactly what is armed. Null when the
 * macro isn't scheduled, its time is malformed, or its end date has passed (no more runs).
 */
fun Macro.nextFireAt(now: LocalDateTime = LocalDateTime.now(), zone: ZoneId = ZoneId.systemDefault()): Long? {
    if (triggerType != TriggerType.SCHEDULED) return null
    val time = scheduledTime ?: return null
    if (parseHhMmOrNull(time) == null) return null
    val next = calculateNextFireTime(
        time, now = now, zone = zone,
        days = daysOfWeek,
        weekInterval = weekInterval,
        anchorEpochDay = anchorEpochDay,
        validUntilEpochDay = validUntilEpochDay,
        jitterSeed = id,
        jitterSpreadMinutes = randomSpreadMinutes,
        randomDays = randomDays()
    )
    // calculateNextFireTime falls back to "tomorrow" once the end date has passed.
    val validUntil = validUntilEpochDay
    if (validUntil != null && Instant.ofEpochMilli(next).atZone(zone).toLocalDate().toEpochDay() > validUntil) return null
    return next
}

private val TimeFmt = DateTimeFormatter.ofPattern("HH:mm")
private val WeekdayFmt = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
private val DateFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
private val DateYearFmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)

/** "today 14:55", "tomorrow 08:15", "Wed 11:13" (within a week), else "Mon 9 Nov 07:07". */
fun formatNextFire(epochMs: Long, now: LocalDateTime = LocalDateTime.now(), zone: ZoneId = ZoneId.systemDefault()): String {
    val at = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDateTime()
    val today: LocalDate = now.toLocalDate()
    val day = at.toLocalDate()
    val time = at.format(TimeFmt)
    return when {
        day == today -> "today $time"
        day == today.plusDays(1) -> "tomorrow $time"
        day.isBefore(today.plusDays(7)) -> "${at.format(WeekdayFmt)} $time"
        day.year == today.year -> "${at.format(DateFmt)} $time"
        else -> "${at.format(DateYearFmt)} $time"
    }
}
