package com.vibeactions.scheduler

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
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
        if (intent.action == ACTION_AI_REGENERATE) {
            regenerate(context, intent)
            return
        }
        if (intent.action != ACTION_AI_SEND) return

        val macroId = intent.getStringExtra(EXTRA_MACRO_ID) ?: return
        // Null for an approved AI variation (scheduled macro): the send then targets the macro's
        // own recipient list instead of a reply counterparty.
        val recipient = intent.getStringExtra(EXTRA_RECIPIENT)
        val body = intent.getStringExtra(EXTRA_BODY) ?: return
        val aiNote = intent.getStringExtra(EXTRA_AI_NOTE)

        // A notification action button doesn't dismiss on tap, so a quick double-tap on "Send"
        // delivers two broadcasts before the cancel above lands — swallow the duplicate.
        val now = System.currentTimeMillis()
        val key = "$macroId|${recipient.orEmpty()}|${body.hashCode()}"
        recentSends.entries.removeAll { now - it.value > DOUBLE_TAP_WINDOW_MS }
        if (recentSends.putIfAbsent(key, now) != null) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                firer.fire(macroId, enforceOncePerDay = false,
                    overrideRecipient = recipient, overrideBody = body, aiNote = aiNote)
            } finally {
                pending.finish()
            }
        }
    }

    /** Re-runs the AI worker for this draft; it posts the new suggestion as a fresh approval. */
    private fun regenerate(context: Context, intent: Intent) {
        val macroId = intent.getStringExtra(EXTRA_MACRO_ID) ?: return
        val previous = intent.getStringExtra(EXTRA_BODY) ?: return
        val recipient = intent.getStringExtra(EXTRA_RECIPIENT)
        val incoming = intent.getStringExtra(EXTRA_INCOMING)
        val eventId = java.util.UUID.randomUUID().toString()
        val work = if (recipient != null) {
            // An auto-reply needs the message it answers; without it there's nothing to rewrite from.
            if (incoming == null) return
            OneTimeWorkRequestBuilder<GeminiReplyWorker>().setInputData(
                workDataOf(
                    GeminiReplyWorker.KEY_MACRO_ID to macroId,
                    GeminiReplyWorker.KEY_SENDER to recipient,
                    GeminiReplyWorker.KEY_BODY to incoming,
                    GeminiReplyWorker.KEY_EVENT_ID to eventId,
                    GeminiReplyWorker.KEY_PREVIOUS to previous
                )
            )
        } else {
            OneTimeWorkRequestBuilder<AiVaryWorker>().setInputData(
                workDataOf(
                    AiVaryWorker.KEY_MACRO_ID to macroId,
                    AiVaryWorker.KEY_EVENT_ID to eventId,
                    AiVaryWorker.KEY_PREVIOUS to previous
                )
            )
        }
        WorkManager.getInstance(context).enqueue(
            work.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build()
        )
        Toast.makeText(context, "Writing a new suggestion…", Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val ACTION_AI_SEND = "com.vibeactions.AI_SEND"
        const val ACTION_AI_REGENERATE = "com.vibeactions.AI_REGENERATE"
        const val EXTRA_INCOMING = "incoming"

        fun regenerateIntent(
            context: Context, macroId: String, recipient: String?, incoming: String?,
            previous: String, notifId: Int
        ) = Intent(context, AiReplyActionReceiver::class.java).apply {
            action = ACTION_AI_REGENERATE
            putExtra(EXTRA_MACRO_ID, macroId)
            recipient?.let { putExtra(EXTRA_RECIPIENT, it) }
            incoming?.let { putExtra(EXTRA_INCOMING, it) }
            putExtra(EXTRA_BODY, previous)
            putExtra(EXTRA_NOTIF_ID, notifId)
        }
        const val ACTION_AI_DISCARD = "com.vibeactions.AI_DISCARD"
        const val EXTRA_MACRO_ID = "macro_id"
        const val EXTRA_RECIPIENT = "recipient"
        const val EXTRA_BODY = "body"
        const val EXTRA_NOTIF_ID = "notif_id"
        const val EXTRA_AI_NOTE = "ai_note"
        private const val DOUBLE_TAP_WINDOW_MS = 5_000L
        // Thread-safe: two taps can dispatch overlapping onReceive calls.
        private val recentSends = ConcurrentHashMap<String, Long>()
    }
}
