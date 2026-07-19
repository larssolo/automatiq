package com.vibeactions.scheduler

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.model.TriggerType
import com.vibeactions.util.callerMatches
import com.vibeactions.util.incomingMatches
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes an incoming event (SMS or missed call) to the macros that should reply to it. Shared by
 * the live receivers and [DeferredReplyWorker] (quiet-hours deferral), so matching, throttling and
 * the AI hand-off behave identically no matter when the reply actually happens.
 */
@Singleton
class IncomingReplyRouter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: MacroRepository,
    private val firer: MacroFirer
) {
    /** Fires every INCOMING macro matching [sender]/[body]: AI macros via [GeminiReplyWorker]
     *  (off any receiver's goAsync budget), plain ones directly. */
    suspend fun handleSms(sender: String, body: String) {
        val now = System.currentTimeMillis()
        pruneThrottle(now)
        repo.getEnabledByTrigger(TriggerType.INCOMING)
            .filter { incomingMatches(it, sender, body) }
            .forEach { macro ->
                if (!claimThrottle(macro.id, sender, now)) return@forEach
                if (macro.aiReplyEnabled) {
                    // The event id identifies THIS incoming SMS: a WorkManager retry re-carries it
                    // (dedup works), while a later identical message mints a new id (and is
                    // answered again).
                    val work = OneTimeWorkRequestBuilder<GeminiReplyWorker>()
                        .setInputData(
                            workDataOf(
                                GeminiReplyWorker.KEY_MACRO_ID to macro.id,
                                GeminiReplyWorker.KEY_SENDER to sender,
                                GeminiReplyWorker.KEY_BODY to body,
                                GeminiReplyWorker.KEY_EVENT_ID to
                                    java.util.UUID.randomUUID().toString()
                            )
                        )
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build()
                    WorkManager.getInstance(context).enqueue(work)
                } else {
                    firer.fire(macro.id, enforceOncePerDay = false, overrideRecipient = sender)
                }
            }
    }

    /** Fires every MISSED_CALL macro whose caller filter matches [number], texting the caller. */
    suspend fun handleMissedCall(number: String) {
        val now = System.currentTimeMillis()
        pruneThrottle(now)
        repo.getEnabledByTrigger(TriggerType.MISSED_CALL)
            .filter { callerMatches(it, number) }
            .forEach { macro ->
                if (!claimThrottle(macro.id, number, now)) return@forEach
                firer.fire(macro.id, enforceOncePerDay = false, overrideRecipient = number)
            }
    }

    /** Per-(macro, other party) throttle so two auto-replying phones can't ping-pong, and repeated
     *  calls/messages within the window get one reply. True = claimed, caller may fire. The
     *  check-and-set is atomic (a single [ConcurrentHashMap.compute]), so two events for the same
     *  (macro, party) arriving on overlapping threads can't both claim and double-reply. */
    private fun claimThrottle(macroId: String, party: String, now: Long): Boolean {
        val key = macroId + "|" + party.filter { it.isDigit() }
        var claimed = false
        lastReply.compute(key) { _, last ->
            if (last != null && now - last < THROTTLE_MS) last
            else { claimed = true; now }
        }
        return claimed
    }

    private fun pruneThrottle(now: Long) {
        lastReply.entries.removeAll { now - it.value > THROTTLE_MS }
    }

    companion object {
        private const val THROTTLE_MS = 60_000L
        // Thread-safe: overlapping broadcasts/workers may route concurrently.
        private val lastReply = ConcurrentHashMap<String, Long>()
    }
}
