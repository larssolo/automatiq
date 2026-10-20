package com.vibeactions.scheduler

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import com.vibeactions.domain.model.ActionType
import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.SoundMode
import com.vibeactions.notifications.MacroNotificationManager
import com.vibeactions.util.expandTemplate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** Runs the non-SMS actions. Returns a short description for the Log, or throws with the reason. */
@Singleton
class MacroActionRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notifications: MacroNotificationManager
) {
    suspend fun run(macro: Macro): String = when (macro.actionType) {
        ActionType.SOUND -> setSound(macro.soundMode)
        ActionType.REMINDER -> {
            val text = expandTemplate(macro.messageBody, LocalDateTime.now(), macro.name)
            notifications.notifyReminder(macro, text)
            "Reminder: $text"
        }
        ActionType.WEBHOOK -> callWebhook(macro)
        ActionType.SMS -> error("SMS is sent by MacroFirer")
    }

    private fun setSound(mode: SoundMode): String {
        val nm = context.getSystemService(NotificationManager::class.java)
        val audio = context.getSystemService(AudioManager::class.java)
        // Silent and Do Not Disturb need the user's DND access grant. Normal/Vibrate usually
        // don't, but Android refuses them too when they'd end an active Do Not Disturb.
        if (needsDndAccess(mode) && !nm.isNotificationPolicyAccessGranted) error(NO_DND_ACCESS)
        try {
            when (mode) {
                SoundMode.NORMAL -> audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
                SoundMode.VIBRATE -> audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                SoundMode.SILENT -> audio.ringerMode = AudioManager.RINGER_MODE_SILENT
                SoundMode.DND_ON -> nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                SoundMode.DND_OFF -> nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
        } catch (e: SecurityException) {
            error(NO_DND_ACCESS)
        }
        return "Sound → ${soundModeLabel(mode)}"
    }

    private suspend fun callWebhook(macro: Macro): String = withContext(Dispatchers.IO) {
        val now = LocalDateTime.now()
        val url = expandTemplate(macro.webhookUrl.orEmpty().trim(), now, macro.name)
        val post = macro.webhookMethod.equals("POST", ignoreCase = true)
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = if (post) "POST" else "GET"
            // Short: this runs inside an alarm/broadcast's time budget.
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            if (post) {
                val body = expandTemplate(macro.messageBody, now, macro.name)
                val json = body.trimStart().startsWith("{") || body.trimStart().startsWith("[")
                conn.setRequestProperty("Content-Type", if (json) "application/json" else "text/plain; charset=utf-8")
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code from ${URL(url).host}")
            "${if (post) "POST" else "GET"} ${URL(url).host} → $code"
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val NO_DND_ACCESS = "Do Not Disturb access not granted — allow it in the macro"

        fun needsDndAccess(mode: SoundMode) =
            mode == SoundMode.SILENT || mode == SoundMode.DND_ON || mode == SoundMode.DND_OFF

        fun soundModeLabel(mode: SoundMode) = when (mode) {
            SoundMode.NORMAL -> "Normal"
            SoundMode.VIBRATE -> "Vibrate"
            SoundMode.SILENT -> "Silent"
            SoundMode.DND_ON -> "Do Not Disturb on"
            SoundMode.DND_OFF -> "Do Not Disturb off"
        }
    }
}
