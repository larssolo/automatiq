package com.vibeactions.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vibeactions.data.repository.MacroLogRepository
import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.model.MacroStatus
import com.vibeactions.notifications.MacroNotificationManager
import com.vibeactions.util.maskPhone
import com.vibeactions.util.smsResultErrorText
import com.vibeactions.widget.WidgetRefresher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives the radio's sent receipt for each dispatched SMS (armed by SmsDispatcher). Success is a
 * no-op — the dispatch path already finalized the log as SUCCESS. On failure the log entry and the
 * macro's status flip to FAILED with the radio's reason, and a corrective notification is posted:
 * without this, "handed to SmsManager" would be reported as sent even when the radio dropped it.
 */
@AndroidEntryPoint
class SmsSentReceiver : BroadcastReceiver() {
    @Inject lateinit var logRepo: MacroLogRepository
    @Inject lateinit var macroRepo: MacroRepository
    @Inject lateinit var notifications: MacroNotificationManager
    @Inject lateinit var widgets: WidgetRefresher

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SMS_SENT) return
        val error = smsResultErrorText(resultCode) ?: return
        val logId = intent.getLongExtra(EXTRA_LOG_ID, -1L)
        val macroId = intent.getStringExtra(EXTRA_MACRO_ID) ?: return
        val recipient = intent.getStringExtra(EXTRA_RECIPIENT) ?: return
        val body = intent.getStringExtra(EXTRA_BODY).orEmpty()
        if (logId == -1L) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                logRepo.updateResult(logId, MacroStatus.FAILED, "to ${maskPhone(recipient)}: $error")
                macroRepo.updateLastStatus(macroId, MacroStatus.FAILED)
                macroRepo.getById(macroId)?.let { macro ->
                    notifications.notifyResult(macro, MacroStatus.FAILED, error, listOf(recipient), body)
                }
                widgets.refreshFor(macroId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SMS_SENT = "com.vibeactions.SMS_SENT"
        const val EXTRA_LOG_ID = "log_id"
        const val EXTRA_MACRO_ID = "macro_id"
        const val EXTRA_RECIPIENT = "recipient"
        const val EXTRA_BODY = "body"
    }
}
