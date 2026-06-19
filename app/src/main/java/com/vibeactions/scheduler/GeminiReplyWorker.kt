package com.vibeactions.scheduler

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.model.AiSendMode
import com.vibeactions.notifications.MacroNotificationManager
import com.vibeactions.util.geminiGenerate
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

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

        val macro = repo.getById(macroId) ?: return Result.success()
        if (!macro.enabled) return Result.success()

        val prefs = applicationContext.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_api_key", "").orEmpty()
        if (apiKey.isBlank()) {
            // No key configured — fall back to the fixed body.
            firer.fire(macroId, enforceOncePerDay = false, overrideRecipient = sender)
            return Result.success()
        }

        val systemPrompt = prefs.getString("gemini_system_prompt", "").orEmpty()
        val generated = runCatching { geminiGenerate(apiKey, systemPrompt, body) }
            .getOrElse { macro.messageBody }

        when (macro.aiSendMode) {
            AiSendMode.APPROVE -> notifications.notifyAiApproval(macro, sender, generated)
            AiSendMode.AUTO -> {
                firer.fire(
                    macroId, enforceOncePerDay = false,
                    overrideRecipient = sender, overrideBody = generated,
                    suppressResultNotification = true
                )
                notifications.notifyAiSent(macro, sender, generated)
            }
        }
        return Result.success()
    }

    companion object {
        const val KEY_MACRO_ID = "macro_id"
        const val KEY_SENDER = "sender"
        const val KEY_BODY = "body"
    }
}
