package com.vibeactions.data

import android.content.Context

/**
 * Behaviour settings persisted to SharedPreferences, read from background threads (the SMS receiver
 * and workers) as well as the Settings UI — so these are plain synchronous getters/setters rather
 * than Compose state. Keys are public so the backup/restore code can round-trip them.
 */
object AppSettings {
    const val PREFS = "app_settings"

    const val KEY_QUIET_ENABLED = "quiet_hours_enabled"
    const val KEY_QUIET_START = "quiet_start_minute"
    const val KEY_QUIET_END = "quiet_end_minute"
    private const val KEY_LAST_CATCHUP = "last_catchup_at"

    /** Default quiet window: 22:00 → 07:00. */
    const val DEFAULT_QUIET_START = 22 * 60
    const val DEFAULT_QUIET_END = 7 * 60

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun quietHoursEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_QUIET_ENABLED, false)

    fun setQuietHoursEnabled(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_QUIET_ENABLED, value).apply()

    fun quietStartMinute(context: Context): Int =
        prefs(context).getInt(KEY_QUIET_START, DEFAULT_QUIET_START)

    fun quietEndMinute(context: Context): Int =
        prefs(context).getInt(KEY_QUIET_END, DEFAULT_QUIET_END)

    fun setQuietWindow(context: Context, startMinute: Int, endMinute: Int) =
        prefs(context).edit()
            .putInt(KEY_QUIET_START, startMinute)
            .putInt(KEY_QUIET_END, endMinute)
            .apply()

    fun lastCatchUpAt(context: Context): Long =
        prefs(context).getLong(KEY_LAST_CATCHUP, 0L)

    fun setLastCatchUpAt(context: Context, at: Long) =
        prefs(context).edit().putLong(KEY_LAST_CATCHUP, at).apply()
}
