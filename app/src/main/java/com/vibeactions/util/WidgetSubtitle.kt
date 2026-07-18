package com.vibeactions.util

import com.vibeactions.domain.model.MacroStatus
import java.time.Instant
import java.time.ZoneId

/**
 * Subtitle for a macro home-screen widget: "Tap to send" until the macro has fired at least once,
 * then "Last: 14:32 ✓" — time of day when the last fire was today, a short date ("Last: 12/7 ✓")
 * otherwise. Pure so it can be JVM-tested.
 */
fun widgetSubtitle(
    lastTriggeredAt: Long?,
    lastStatus: MacroStatus?,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault()
): String {
    if (lastTriggeredAt == null) return "Tap to send"
    val last = Instant.ofEpochMilli(lastTriggeredAt).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val stamp = if (last.toLocalDate() == today) {
        "%02d:%02d".format(last.hour, last.minute)
    } else {
        "${last.dayOfMonth}/${last.monthValue}"
    }
    val mark = when (lastStatus) {
        MacroStatus.SUCCESS -> " ✓"
        MacroStatus.FAILED -> " ✗"
        else -> ""      // PENDING/null: outcome unknown, show the time alone
    }
    return "Last: $stamp$mark"
}
