package com.vibeactions.scheduler

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.model.AiSendMode
import com.vibeactions.notifications.MacroNotificationManager
import com.vibeactions.util.DEFAULT_GEMINI_MODEL
import com.vibeactions.util.aiReplyDedupKey
import com.vibeactions.util.expandTemplate
import com.vibeactions.util.geminiGenerate
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDateTime

/**
 * Generates an AI auto-reply and either posts an approval notification (APPROVE) or sends it and
 * posts an info notification (AUTO). Runs as a WorkManager job rather than inline in the SMS
 * receiver so the Gemini call (up to ~25s) cannot exceed the BroadcastReceiver's goAsync budget.
 * Falls back to the macro's fixed body when there is no API key or the call fails.
 */
@HiltWorker
class GeminiReplyWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repo: MacroRepository,
    private val firer: MacroFirer,
    private val notifications: MacroNotificationManager
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val macroId = inputData.getString(KEY_MACRO_ID) ?: return Result.failure()
        val sender = inputData.getString(KEY_SENDER) ?: return Result.failure()
        val body = inputData.getString(KEY_BODY) ?: return Result.failure()
        // Fallback for jobs enqueued by an older app version whose inputData had no event id.
        val eventId = inputData.getString(KEY_EVENT_ID) ?: body.hashCode().toString()

        val macro = repo.getById(macroId) ?: return Result.success()
        if (!macro.enabled) return Result.success()

        val prefs = applicationContext.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_api_key", "").orEmpty()
        if (apiKey.isBlank()) {
            // No key configured — fall back to the fixed body.
            firer.fire(macroId, enforceOncePerDay = false, overrideRecipient = sender)
            return Result.success()
        }

        val model = prefs.getString("gemini_model", DEFAULT_GEMINI_MODEL)
            ?.ifBlank { DEFAULT_GEMINI_MODEL } ?: DEFAULT_GEMINI_MODEL
        // Per-macro instruction wins; otherwise fall back to the global Settings prompt.
        val instruction = macro.aiReplyInstruction?.takeIf { it.isNotBlank() }
            ?: prefs.getString("gemini_system_prompt", "")?.takeIf { it.isNotBlank() }
        // A fixed wrapper keeps replies short and on-point (the AI otherwise rambles).
        val systemPrompt = buildString {
            append("You write ONE short SMS reply (at most 2 sentences). ")
            append("Reply ONLY with the message itself — no preamble, no explanation, no quotes. ")
            if (instruction != null) append("Reply like this: $instruction")
        }
        val generated = runCatching {
            geminiGenerate(apiKey, systemPrompt, body, model, maxOutputTokens = 150)
        }.getOrElse {
            // Gemini unreachable → send the fixed fallback body, expanded like any other reply
            // (MacroFirer never expands an overrideBody, so a raw {afsender}/{dato} would otherwise
            // go out literally — unlike the no-API-key path which expands via MacroFirer).
            expandTemplate(macro.messageBody, LocalDateTime.now(), macro.name, sender)
        }

        when (macro.aiSendMode) {
            AiSendMode.APPROVE -> notifications.notifyAiApproval(macro, sender, generated)
            AiSendMode.AUTO -> {
                // Claim this (macro, sender, event, day) before sending. If WorkManager re-runs a
                // worker that died mid-flight, the retry carries identical inputData → identical key
                // → the claim fails and we don't send the auto-reply twice.
                if (!claimAutoSend(macroId, sender, eventId)) return Result.success()
                val result = firer.fire(
                    macroId, enforceOncePerDay = false,
                    overrideRecipient = sender, overrideBody = generated,
                    suppressResultNotification = true
                )
                // Only announce success when the send actually succeeded; a failed send would
                // otherwise be reported as "AI reply sent" with no failure notice at all.
                if (result?.status == com.vibeactions.domain.model.MacroStatus.SUCCESS) {
                    notifications.notifyAiSent(macro, sender, generated)
                } else if (result != null) {
                    notifications.notifyResult(macro, result.status, result.error, listOf(sender), generated)
                }
            }
        }
        return Result.success()
    }

    /** True if this auto-send was newly claimed; false if an earlier run already claimed it today.
     *  Backed by SharedPreferences, pruned to the current day so it can't grow unbounded. */
    private fun claimAutoSend(macroId: String, sender: String, eventId: String): Boolean {
        val now = System.currentTimeMillis()
        val key = aiReplyDedupKey(macroId, sender, eventId, now)
        val todayToken = "|" + java.time.Instant.ofEpochMilli(now)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay() + "|"
        val prefs = applicationContext.getSharedPreferences("ai_sent", Context.MODE_PRIVATE)
        synchronized(claimLock) {
            val current = prefs.getStringSet(KEY_SENT, emptySet()).orEmpty()
            if (key in current) return false
            // Keep only today's keys plus the new one.
            val pruned = current.filter { todayToken in it }.toMutableSet().apply { add(key) }
            prefs.edit().putStringSet(KEY_SENT, pruned).commit()
            return true
        }
    }

    companion object {
        const val KEY_MACRO_ID = "macro_id"
        const val KEY_SENDER = "sender"
        const val KEY_BODY = "body"
        const val KEY_EVENT_ID = "event_id"
        private const val KEY_SENT = "sent_keys"
        // Guards the read-modify-write of the SharedPreferences claim set across overlapping workers.
        private val claimLock = Any()
    }
}
