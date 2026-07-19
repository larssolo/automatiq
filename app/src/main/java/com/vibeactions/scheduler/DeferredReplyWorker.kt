package com.vibeactions.scheduler

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.vibeactions.data.AppSettings
import com.vibeactions.util.minutesUntilQuietEnd
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Delivers an auto-reply that arrived during quiet hours, shortly after the window ends. One job
 * per (kind, other party): a later message the same night REPLACEs the pending job, so at the end
 * of the window the person gets one reply to their latest message — not one text per attempt.
 */
@HiltWorker
class DeferredReplyWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val router: IncomingReplyRouter
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val kind = inputData.getString(KEY_KIND) ?: return Result.failure()
        val sender = inputData.getString(KEY_SENDER) ?: return Result.failure()
        val body = inputData.getString(KEY_BODY).orEmpty()
        // The user may have moved or extended the window while this job waited — re-defer if the
        // clock still lands inside it (also covers WorkManager firing a little early).
        if (quietMinutesLeft(applicationContext) > 0) {
            enqueue(applicationContext, kind, sender, body)
            return Result.success()
        }
        // Persistent one-shot claim: if this worker is killed after dispatching but before it
        // returns success, WorkManager re-runs doWork from scratch. The router's throttle is
        // in-memory and gone after a process death, so without this the deferred reply double-sends.
        if (!claimSend(applicationContext, kind, sender, body)) return Result.success()
        when (kind) {
            KIND_CALL -> router.handleMissedCall(sender)
            else -> router.handleSms(sender, body)
        }
        return Result.success()
    }

    /** True if this (kind, party, message, day) was newly claimed; false if an earlier run already
     *  sent it. Backed by SharedPreferences, pruned to the current day so it can't grow unbounded. */
    private fun claimSend(context: Context, kind: String, party: String, body: String): Boolean {
        val now = System.currentTimeMillis()
        val day = java.time.Instant.ofEpochMilli(now)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
        val dayToken = "|$day|"
        val key = "$kind|${party.filter { it.isDigit() }}$dayToken${body.hashCode()}"
        val prefs = context.getSharedPreferences("deferred_sent", Context.MODE_PRIVATE)
        synchronized(claimLock) {
            val current = prefs.getStringSet(KEY_SENT, emptySet()).orEmpty()
            if (key in current) return false
            val pruned = current.filter { dayToken in it }.toMutableSet().apply { add(key) }
            prefs.edit().putStringSet(KEY_SENT, pruned).commit()
            return true
        }
    }

    companion object {
        const val KIND_SMS = "sms"
        const val KIND_CALL = "call"
        const val KEY_KIND = "kind"
        const val KEY_SENDER = "sender"
        const val KEY_BODY = "body"
        private const val KEY_SENT = "sent_keys"
        // Guards the read-modify-write of the claim set across overlapping worker runs.
        private val claimLock = Any()

        /** Minutes until the quiet window ends right now; 0 when quiet hours don't apply. */
        fun quietMinutesLeft(context: Context): Int {
            if (!AppSettings.quietHoursEnabled(context)) return 0
            val now = LocalTime.now()
            return minutesUntilQuietEnd(
                now.hour * 60 + now.minute,
                AppSettings.quietStartMinute(context),
                AppSettings.quietEndMinute(context)
            )
        }

        /** Schedules (or REPLACEs) the deferred reply for this (kind, other party) to run one
         *  minute after the quiet window closes. */
        fun enqueue(context: Context, kind: String, sender: String, body: String) {
            val delayMinutes = (quietMinutesLeft(context) + 1).toLong()
            val work = OneTimeWorkRequestBuilder<DeferredReplyWorker>()
                .setInputData(workDataOf(KEY_KIND to kind, KEY_SENDER to sender, KEY_BODY to body))
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "deferred_reply|$kind|" + sender.filter { it.isDigit() },
                ExistingWorkPolicy.REPLACE, work
            )
        }
    }
}
