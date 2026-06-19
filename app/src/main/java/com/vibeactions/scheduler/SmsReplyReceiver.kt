package com.vibeactions.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.model.AiSendMode
import com.vibeactions.domain.model.TriggerType
import com.vibeactions.notifications.MacroNotificationManager
import com.vibeactions.util.geminiGenerate
import com.vibeactions.util.incomingMatches
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Listens for incoming SMS and fires any matching INCOMING (auto-reply) macro, replying to the
 * sender. A short per-(macro, sender) throttle prevents two auto-replying phones from ping-ponging.
 * When aiReplyEnabled, calls Gemini to generate the reply; falls back to messageBody on error.
 */
@AndroidEntryPoint
class SmsReplyReceiver : BroadcastReceiver() {
    @Inject lateinit var repo: MacroRepository
    @Inject lateinit var firer: MacroFirer
    @Inject lateinit var notifications: MacroNotificationManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val sender = messages.firstOrNull()?.displayOriginatingAddress ?: return
        val body = messages.joinToString("") { it.messageBody ?: "" }
        if (body.isBlank()) return

        val prefs = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                repo.getEnabledByTrigger(TriggerType.INCOMING)
                    .filter { incomingMatches(it, sender, body) }
                    .forEach { macro ->
                        val key = macro.id + "|" + sender.filter { c -> c.isDigit() }
                        val last = lastReply[key]
                        if (last != null && now - last < THROTTLE_MS) return@forEach
                        lastReply[key] = now

                        if (macro.aiReplyEnabled) {
                            val apiKey = prefs.getString("gemini_api_key", "") ?: ""
                            if (apiKey.isBlank()) {
                                // No key configured — fall back to fixed body.
                                firer.fire(macro.id, enforceOncePerDay = false,
                                    overrideRecipient = sender)
                                return@forEach
                            }
                            val systemPrompt = prefs.getString("gemini_system_prompt", "") ?: ""
                            val generated = runCatching {
                                geminiGenerate(apiKey, systemPrompt, body)
                            }.getOrElse { macro.messageBody }

                            when (macro.aiSendMode) {
                                AiSendMode.APPROVE -> {
                                    notifications.notifyAiApproval(macro, sender, generated)
                                }
                                AiSendMode.AUTO -> {
                                    firer.fire(
                                        macro.id, enforceOncePerDay = false,
                                        overrideRecipient = sender,
                                        overrideBody = generated,
                                        suppressResultNotification = true
                                    )
                                    notifications.notifyAiSent(macro, sender, generated)
                                }
                            }
                        } else {
                            firer.fire(macro.id, enforceOncePerDay = false, overrideRecipient = sender)
                        }
                    }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val THROTTLE_MS = 60_000L
        private val lastReply = mutableMapOf<String, Long>()
    }
}
