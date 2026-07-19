package com.vibeactions.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.vibeactions.data.AppSettings
import com.vibeactions.util.isWithinQuietHours
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

/**
 * Listens for incoming SMS and routes them to matching INCOMING (auto-reply) macros via
 * [IncomingReplyRouter]. During quiet hours the reply is not dropped but deferred:
 * [DeferredReplyWorker] answers the sender's latest message shortly after the window ends.
 */
@AndroidEntryPoint
class SmsReplyReceiver : BroadcastReceiver() {
    @Inject lateinit var router: IncomingReplyRouter

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val sender = messages.firstOrNull()?.displayOriginatingAddress ?: return
        val body = messages.joinToString("") { it.messageBody ?: "" }
        if (body.isBlank()) return
        // Alphanumeric sender IDs (DHL, banks, OTP gateways) cannot receive SMS — replying would
        // just produce a radio failure (and, for AI macros, a wasted Gemini call).
        if (sender.none { it.isDigit() }) return
        // Quiet hours: hold ALL auto-replies (including AI approval prompts) and answer just after
        // the window ends. Scheduled and manual sends are unaffected.
        if (AppSettings.quietHoursEnabled(context) && isWithinQuietHours(
                LocalTime.now(),
                AppSettings.quietStartMinute(context),
                AppSettings.quietEndMinute(context)
            )
        ) {
            // Hold the process while WorkManager persists the deferral, else a process kill right
            // after onReceive returns could lose it.
            val pending = goAsync()
            try {
                DeferredReplyWorker.enqueue(context, DeferredReplyWorker.KIND_SMS, sender, body)
            } finally {
                pending.finish()
            }
            return
        }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                router.handleSms(sender, body)
            } finally {
                pending.finish()
            }
        }
    }
}
