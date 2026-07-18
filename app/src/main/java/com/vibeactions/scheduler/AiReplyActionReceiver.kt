package com.vibeactions.scheduler

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@AndroidEntryPoint
class AiReplyActionReceiver : BroadcastReceiver() {
    @Inject lateinit var firer: MacroFirer

    override fun onReceive(context: Context, intent: Intent) {
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
        if (notifId != -1) {
            context.getSystemService(NotificationManager::class.java)?.cancel(notifId)
        }
        if (intent.action != ACTION_AI_SEND) return

        val macroId = intent.getStringExtra(EXTRA_MACRO_ID) ?: return
        val recipient = intent.getStringExtra(EXTRA_RECIPIENT) ?: return
        val body = intent.getStringExtra(EXTRA_BODY) ?: return

        // A notification action button doesn't dismiss on tap, so a quick double-tap on "Send"
        // delivers two broadcasts before the cancel above lands — swallow the duplicate.
        val now = System.currentTimeMillis()
        val key = "$macroId|$recipient|${body.hashCode()}"
        recentSends.entries.removeAll { now - it.value > DOUBLE_TAP_WINDOW_MS }
        if (recentSends.putIfAbsent(key, now) != null) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                firer.fire(macroId, enforceOncePerDay = false,
                    overrideRecipient = recipient, overrideBody = body)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_AI_SEND = "com.vibeactions.AI_SEND"
        const val ACTION_AI_DISCARD = "com.vibeactions.AI_DISCARD"
        const val EXTRA_MACRO_ID = "macro_id"
        const val EXTRA_RECIPIENT = "recipient"
        const val EXTRA_BODY = "body"
        const val EXTRA_NOTIF_ID = "notif_id"
        private const val DOUBLE_TAP_WINDOW_MS = 5_000L
        // Thread-safe: two taps can dispatch overlapping onReceive calls.
        private val recentSends = ConcurrentHashMap<String, Long>()
    }
}
