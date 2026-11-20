package com.vibeactions.billing

import android.content.Context
import com.vibeactions.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Where the user stands: free trial, trial over, or bought. [Trial.daysLeft] counts calendar days
 * to the day the trial ends: 30 on the first day, one less each new day, 0 on the last day.
 */
sealed interface LicenseStatus {
    data class Trial(val daysLeft: Int) : LicenseStatus
    data object Expired : LicenseStatus
    data object Unlocked : LicenseStatus
}

const val TRIAL_DAYS = 30
private const val DAY_MS = 24 * 60 * 60_000L

/**
 * The trial/unlock rule, kept pure for tests. [now] is clamped to [lastSeen] so winding the clock
 * back can't stretch a trial; the trial ends exactly [TRIAL_DAYS] days after [trialStart].
 * [daysLeft][LicenseStatus.Trial.daysLeft] counts calendar days in [zone], the way people count:
 * each new day is one less, whatever the time.
 */
fun licenseStatus(
    paywall: Boolean, unlocked: Boolean, trialStart: Long, lastSeen: Long, now: Long,
    zone: ZoneId = ZoneId.systemDefault()
): LicenseStatus {
    if (!paywall || unlocked) return LicenseStatus.Unlocked
    val effectiveNow = maxOf(now, lastSeen)
    val end = trialStart + TRIAL_DAYS * DAY_MS
    if (end <= effectiveNow) return LicenseStatus.Expired
    fun date(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    return LicenseStatus.Trial(ChronoUnit.DAYS.between(date(effectiveNow), date(end)).toInt().coerceIn(0, TRIAL_DAYS))
}

/**
 * The app's licence: a [TRIAL_DAYS]-day trial counted from the first run of a paywalled build,
 * then a one-time Play purchase ([BillingManager]). Stored in its own prefs file, which Android's
 * auto backup includes, so a reinstall restored from backup keeps its original trial start.
 *
 * The full (sideloaded) flavor has no paywall and is always [LicenseStatus.Unlocked].
 */
object License {
    private const val PREFS = "license"
    private const val KEY_TRIAL_START = "trial_start"
    private const val KEY_LAST_SEEN = "last_seen"
    private const val KEY_UNLOCKED = "unlocked"
    private const val KEY_WARNED = "warned_ending"
    private const val KEY_NOTIFIED_EXPIRED = "notified_expired"

    /** Days before the end when the "trial ends soon" notification and banner appear. */
    const val WARN_DAYS = 5

    private val _status = MutableStateFlow<LicenseStatus>(LicenseStatus.Unlocked)
    /** Live status for the UI; [refresh] re-reads it. */
    val status: StateFlow<LicenseStatus> = _status.asStateFlow()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Current status. The first call on a paywalled build starts the trial. */
    fun current(context: Context, now: Long = System.currentTimeMillis()): LicenseStatus {
        val p = prefs(context)
        val start = p.getLong(KEY_TRIAL_START, 0L).takeIf { it > 0 }
            ?: now.also { p.edit().putLong(KEY_TRIAL_START, it).apply() }
        val lastSeen = p.getLong(KEY_LAST_SEEN, 0L)
        if (now > lastSeen) p.edit().putLong(KEY_LAST_SEEN, now).apply()
        return licenseStatus(BuildConfig.PAYWALL, p.getBoolean(KEY_UNLOCKED, false), start, lastSeen, now)
            .also { _status.value = it }
    }

    /** False once the trial is over and nothing was bought: macros stop sending. */
    fun canRun(context: Context): Boolean = current(context) != LicenseStatus.Expired

    fun refresh(context: Context) { current(context) }

    /** Called with Play's answer. A refund comes back as `false` and locks the app again. */
    fun setUnlocked(context: Context, unlocked: Boolean) {
        prefs(context).edit().putBoolean(KEY_UNLOCKED, unlocked).apply()
        refresh(context)
    }

    /** True once per trial when it is about to end; the caller posts the reminder. */
    fun claimEndingWarning(context: Context): Boolean {
        val s = current(context)
        if (s !is LicenseStatus.Trial || s.daysLeft > WARN_DAYS) return false
        return claim(context, KEY_WARNED)
    }

    /** True once when the trial has ended; the caller posts the "macros paused" notice. */
    fun claimExpiredNotice(context: Context): Boolean =
        current(context) == LicenseStatus.Expired && claim(context, KEY_NOTIFIED_EXPIRED)

    private fun claim(context: Context, key: String): Boolean = synchronized(this) {
        val p = prefs(context)
        if (p.getBoolean(key, false)) false
        else { p.edit().putBoolean(key, true).commit(); true }
    }
}
