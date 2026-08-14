package com.vibeactions.scheduler

import android.content.Context
import com.vibeactions.util.aiReplyDedupKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-shot claim for AI auto-sends, shared by [GeminiReplyWorker] (auto-replies) and
 * [AiVaryWorker] (scheduled variations). If WorkManager re-runs a worker that died mid-flight,
 * the retry carries identical inputData — including the event id — so the key is identical, the
 * claim fails, and the same message is never sent twice. Backed by SharedPreferences ("ai_sent",
 * excluded from backups), pruned to the current day so it can't grow unbounded.
 */
@Singleton
class AiAutoSendClaim @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** True if this send was newly claimed; false if an earlier run already claimed it today.
     *  [party] is the counterparty for replies; pass "" for a macro's own recipient list. */
    fun claim(macroId: String, party: String, eventId: String): Boolean {
        val now = System.currentTimeMillis()
        val key = aiReplyDedupKey(macroId, party, eventId, now)
        val todayToken = "|" + java.time.Instant.ofEpochMilli(now)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay() + "|"
        val prefs = context.getSharedPreferences("ai_sent", Context.MODE_PRIVATE)
        synchronized(claimLock) {
            val current = prefs.getStringSet(KEY_SENT, emptySet()).orEmpty()
            if (key in current) return false
            // Keep only today's keys plus the new one.
            val pruned = current.filter { todayToken in it }.toMutableSet().apply { add(key) }
            prefs.edit().putStringSet(KEY_SENT, pruned).commit()
            return true
        }
    }

    companion object {
        private const val KEY_SENT = "sent_keys"
        // Guards the read-modify-write of the SharedPreferences claim set across overlapping workers.
        private val claimLock = Any()
    }
}
