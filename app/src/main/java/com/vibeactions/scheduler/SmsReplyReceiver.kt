package com.vibeactions.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.vibeactions.data.AppSettings
import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.model.TriggerType
import com.vibeactions.util.incomingMatches
import com.vibeactions.util.isWithinQuietHours
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Listens for incoming SMS and fires any matching INCOMING (auto-reply) macro, replying to the
 * sender. A short per-(macro, sender) throttle prevents two auto-replying phones from ping-ponging.
 * AI replies are delegated to [GeminiReplyWorker] (off the receiver's goAsync budget); plain
 * auto-replies fire directly since they only send an SMS.
 */
@AndroidEntryPoint
class SmsReplyReceiver : BroadcastReceiver() {
    @Inject lateinit var repo: MacroRepository
    @Inject lateinit var firer: MacroFirer

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val sender = messages.firstOrNull()?.displayOriginatingAddress ?: return
        val body = messages.joinToString("") { it.messageBody ?: "" }
        if (body.isBlank()) return
        // Alphanumeric sender IDs (DHL, banks, OTP gateways) cannot receive SMS — replying would
        // just produce a radio failure (and, for AI macros, a wasted Gemini call).
        if (sender.none { it.isDigit() }) return
        // Quiet hours: pause ALL auto-replies (including AI approval prompts) during the window, so
        // the phone stays silent at night. Scheduled/manual sends are unaffected.
        if (AppSettings.quietHoursEnabled(context) && isWithinQuietHours(
                LocalTime.now(),
                AppSettings.quietStartMinute(context),
                AppSettings.quietEndMinute(context)
            )
        ) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                pruneThrottle(now)
                repo.getEnabledByTrigger(TriggerType.INCOMING)
                    .filter { incomingMatches(it, sender, body) }
                    .forEach { macro ->
                        val key = macro.id + "|" + sender.filter { c -> c.isDigit() }
                        val last = lastReply[key]
                        if (last != null && now - last < THROTTLE_MS) return@forEach
                        lastReply[key] = now

                        if (macro.aiReplyEnabled) {
                            // Hand off to a worker — the Gemini call can take longer than a
                            // BroadcastReceiver may safely stay alive. The event id identifies THIS
                            // incoming SMS: a WorkManager retry re-carries it (dedup works), while a
                            // later identical message mints a new id (and is answered again).
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
            } finally {
                pending.finish()
            }
        }
    }

    private fun pruneThrottle(now: Long) {
        lastReply.entries.removeAll { now - it.value > THROTTLE_MS }
    }

    companion object {
        private const val THROTTLE_MS = 60_000L
        // Thread-safe: multiple SMS broadcasts may dispatch overlapping coroutines.
        private val lastReply = ConcurrentHashMap<String, Long>()
    }
}
