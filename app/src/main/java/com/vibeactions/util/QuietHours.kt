package com.vibeactions.util

import java.time.LocalTime

/**
 * Whether [minuteOfDay] (0..1439) falls inside the quiet window [startMin, endMin). The window may
 * wrap past midnight (e.g. 22:00 → 07:00, i.e. start > end); start == end is treated as an empty
 * window (never quiet), so a mis-set 00:00–00:00 can't silence every send.
 */
fun isWithinQuietHours(minuteOfDay: Int, startMin: Int, endMin: Int): Boolean {
    if (startMin == endMin) return false
    return if (startMin < endMin) minuteOfDay in startMin until endMin
    else minuteOfDay >= startMin || minuteOfDay < endMin
}

/** Convenience overload taking a [LocalTime]. */
fun isWithinQuietHours(now: LocalTime, startMin: Int, endMin: Int): Boolean =
    isWithinQuietHours(now.hour * 60 + now.minute, startMin, endMin)

/**
 * Minutes from [minuteOfDay] until the quiet window ends, or 0 when outside the window (or the
 * window is empty). Used to delay a deferred auto-reply to just after the window closes.
 */
fun minutesUntilQuietEnd(minuteOfDay: Int, startMin: Int, endMin: Int): Int {
    if (!isWithinQuietHours(minuteOfDay, startMin, endMin)) return 0
    return ((endMin - minuteOfDay) + 1440) % 1440
}

/** Formats a minute-of-day (0..1439) as "HH:mm". */
fun formatMinuteOfDay(minuteOfDay: Int): String {
    val m = ((minuteOfDay % 1440) + 1440) % 1440
    return "%02d:%02d".format(m / 60, m % 60)
}
