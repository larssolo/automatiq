package com.vibeactions.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.MacroStatus
import com.vibeactions.scheduler.AiReplyActionReceiver
import com.vibeactions.util.maskPhone
import com.vibeactions.util.maskRecipients
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MacroNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Macro Actions", NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = "Status of scheduled and manual SMS macros"
        manager.createNotificationChannel(channel)
    }

    fun notifyResult(macro: Macro, status: MacroStatus, error: String?) {
        val title = if (status == MacroStatus.SUCCESS) "Sent: ${macro.name}" else "Failed: ${macro.name}"
        val text = if (status == MacroStatus.SUCCESS)
            "To ${maskRecipients(macro.recipients)}"
        else
            "To ${maskRecipients(macro.recipients)} — ${error ?: "unknown error"}"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)

        if (status == MacroStatus.FAILED) {
            builder.addAction(0, "Retry", retryIntent(macro.id))
            builder.addAction(0, "View Log", openLogIntent())
        }
        manager.notify(macro.id.hashCode(), builder.build())
    }

    private fun retryIntent(macroId: String): PendingIntent {
        val intent = Intent(context, com.vibeactions.scheduler.MacroAlarmReceiver::class.java).apply {
            putExtra(com.vibeactions.scheduler.MacroAlarmReceiver.EXTRA_MACRO_ID, macroId)
            putExtra("force", true)
        }
        return PendingIntent.getBroadcast(
            context, ("retry" + macroId).hashCode() and 0x7FFFFFFF, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openLogIntent(): PendingIntent {
        val intent = Intent(context, com.vibeactions.ui.MainActivity::class.java).apply {
            putExtra("nav", "log")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Posts a notification with Send/Discard action buttons for APPROVE-mode AI replies. */
    fun notifyAiApproval(macro: Macro, recipient: String, generatedBody: String) {
        val notifId = ("ai_approve" + macro.id + recipient).hashCode() and 0x7FFFFFFF
        val preview = if (generatedBody.length > 200) generatedBody.take(200) + "…" else generatedBody

        val sendIntent = Intent(context, AiReplyActionReceiver::class.java).apply {
            action = AiReplyActionReceiver.ACTION_AI_SEND
            putExtra(AiReplyActionReceiver.EXTRA_MACRO_ID, macro.id)
            putExtra(AiReplyActionReceiver.EXTRA_RECIPIENT, recipient)
            putExtra(AiReplyActionReceiver.EXTRA_BODY, generatedBody)
            putExtra(AiReplyActionReceiver.EXTRA_NOTIF_ID, notifId)
        }
        val sendPi = PendingIntent.getBroadcast(
            context, ("ai_send" + macro.id + recipient).hashCode() and 0x7FFFFFFF, sendIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val discardIntent = Intent(context, AiReplyActionReceiver::class.java).apply {
            action = AiReplyActionReceiver.ACTION_AI_DISCARD
            putExtra(AiReplyActionReceiver.EXTRA_NOTIF_ID, notifId)
        }
        val discardPi = PendingIntent.getBroadcast(
            context, ("ai_discard" + macro.id + recipient).hashCode() and 0x7FFFFFFF, discardIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("AI-svar klar til ${maskPhone(recipient)}")
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setAutoCancel(false)
            .addAction(0, "Send", sendPi)
            .addAction(0, "Slet", discardPi)
        manager.notify(notifId, builder.build())
    }

    /** Posts an informational notification after an AUTO-mode AI reply is sent (shows content). */
    fun notifyAiSent(macro: Macro, recipient: String, sentBody: String) {
        val preview = if (sentBody.length > 200) sentBody.take(200) + "…" else sentBody
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("AI-svar sendt til ${maskPhone(recipient)}")
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setAutoCancel(true)
        manager.notify(("ai_sent" + macro.id + recipient).hashCode() and 0x7FFFFFFF, builder.build())
    }

    companion object { const val CHANNEL_ID = "macro_actions" }
}
