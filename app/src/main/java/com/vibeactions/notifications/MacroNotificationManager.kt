package com.vibeactions.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.MacroStatus
import com.vibeactions.util.maskPhone
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
            "To ${maskPhone(macro.recipientNumber)}"
        else
            "To ${maskPhone(macro.recipientNumber)} — ${error ?: "unknown error"}"

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

    companion object { const val CHANNEL_ID = "macro_actions" }
}
