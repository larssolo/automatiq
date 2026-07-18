package com.vibeactions.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.MacroStatus
import com.vibeactions.domain.model.TriggerType
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
        // Keep the message body off the lock screen: the result notification supplies a redacted
        // public version (title only); the full text shows only once the device is unlocked.
        channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        manager.createNotificationChannel(channel)
        // Separate high-importance channel so AI-approval prompts surface as a heads-up. On Android 8+
        // the channel importance — not the per-notification priority — governs heads-up behaviour.
        val aiChannel = NotificationChannel(CHANNEL_ID_AI, "AI Replies", NotificationManager.IMPORTANCE_HIGH)
        aiChannel.description = "AI-generated SMS replies awaiting approval"
        manager.createNotificationChannel(aiChannel)
    }

    fun notifyResult(
        macro: Macro,
        status: MacroStatus,
        error: String?,
        recipients: List<String> = macro.recipients,
        sentBody: String = ""
    ) {
        val title = if (status == MacroStatus.SUCCESS) "Sent: ${macro.name}" else "Failed: ${macro.name}"
        val to = maskRecipients(recipients).ifBlank { "recipient" }
        // Expanded content: who it went to plus the actual message, so tapping/expanding (after
        // unlock) shows exactly what was sent. The body stays off the lock screen via the public
        // version below.
        val detail = buildString {
            append("To $to")
            if (status == MacroStatus.FAILED) append(" — ${error ?: "unknown error"}")
            if (sentBody.isNotBlank()) append("\n\n").append(sentBody)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText("To $to")
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(openLogIntent())
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(redactedResult(title))
            .setAutoCancel(true)

        if (status == MacroStatus.FAILED) {
            // Retry re-fires the macro via its own recipient list, so it only makes sense when the
            // macro can re-fire on its own. Auto-reply (INCOMING) macros reply to the incoming
            // sender — a retry would target a recipient list that never applies (possibly stale
            // numbers from a previous trigger type), so they never get the action.
            if (macro.recipients.isNotEmpty() && macro.triggerType != TriggerType.INCOMING) {
                builder.addAction(0, "Retry", retryIntent(macro.id))
            }
            builder.addAction(0, "View Log", openLogIntent())
        }
        manager.notify(macro.id.hashCode(), builder.build())
    }

    /** Lock-screen-safe version of a result notification: title only, no recipient or message body. */
    private fun redactedResult(title: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setAutoCancel(true)
            .build()

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
        // Tapping the body opens an in-app approve/edit dialog — the reliable path on OEM skins
        // (MIUI) that collapse notifications and never render the Send/Discard action buttons.
        val openIntent = Intent(context, com.vibeactions.ui.MainActivity::class.java).apply {
            putExtra(com.vibeactions.ui.MainActivity.EXTRA_AI_ACTION, "approve")
            putExtra(com.vibeactions.ui.MainActivity.EXTRA_MACRO_ID, macro.id)
            putExtra(com.vibeactions.ui.MainActivity.EXTRA_RECIPIENT, recipient)
            putExtra(com.vibeactions.ui.MainActivity.EXTRA_BODY, generatedBody)
            putExtra(com.vibeactions.ui.MainActivity.EXTRA_NOTIF_ID, notifId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPi = PendingIntent.getActivity(
            context, ("ai_open" + macro.id + recipient).hashCode() and 0x7FFFFFFF, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_AI)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("AI reply ready for ${maskPhone(recipient)}")
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_send, "Send", sendPi)
            .addAction(android.R.drawable.ic_menu_delete, "Discard", discardPi)
        manager.notify(notifId, builder.build())
    }

    /** Warns that a LOCATION macro's geofence couldn't be registered, so it silently won't fire —
     *  otherwise the user assumes the macro is active. */
    fun notifyGeofenceError(macro: Macro, reason: String?) {
        val detail = "\"${macro.name}\" won't fire: ${reason ?: "the location trigger couldn't be registered"}. " +
            "Open the macro and set location access to \"Allow all the time\"."
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Location macro not active: ${macro.name}")
            .setContentText(reason ?: "Couldn't register the location trigger")
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(openLogIntent())
            .setAutoCancel(true)
        manager.notify(("geofence_err" + macro.id).hashCode(), builder.build())
    }

    /** Posts an informational notification after an AUTO-mode AI reply is sent (shows content). */
    fun notifyAiSent(macro: Macro, recipient: String, sentBody: String) {
        val preview = if (sentBody.length > 200) sentBody.take(200) + "…" else sentBody
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("AI reply sent to ${maskPhone(recipient)}")
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setAutoCancel(true)
        manager.notify(("ai_sent" + macro.id + recipient).hashCode() and 0x7FFFFFFF, builder.build())
    }

    companion object {
        const val CHANNEL_ID = "macro_actions"
        const val CHANNEL_ID_AI = "macro_ai"
    }
}
