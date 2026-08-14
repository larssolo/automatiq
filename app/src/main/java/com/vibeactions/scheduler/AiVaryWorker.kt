package com.vibeactions.scheduler

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.model.AiSendMode
import com.vibeactions.notifications.MacroNotificationManager
import com.vibeactions.util.DEFAULT_GEMINI_MODEL
import com.vibeactions.util.buildVaryPrompt
import com.vibeactions.util.expandTemplate
import com.vibeactions.util.geminiGenerate
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import java.time.LocalDateTime
import kotlin.coroutines.coroutineContext

/**
 * Generates an AI-written variation of a non-reply macro's fixed message and either posts an
 * approval notification (APPROVE) or sends it straight away (AUTO), so recipients of a recurring
 * send never get the exact same text twice. Enqueued by [MacroFirer] when an AI-variation macro
 * fires — the Gemini call (up to ~25s) cannot run inline on a BroadcastReceiver's goAsync budget.
 * Falls back to the macro's own message when there is no API key or the call fails: a scheduled
 * send whose day-claim is already consumed must never be silently dropped.
 */
@HiltWorker
class AiVaryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repo: MacroRepository,
    private val firer: MacroFirer,
    private val notifications: MacroNotificationManager,
    private val autoSendClaim: AiAutoSendClaim
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val macroId = inputData.getString(KEY_MACRO_ID) ?: return Result.failure()
        val eventId = inputData.getString(KEY_EVENT_ID) ?: return Result.failure()

        val macro = repo.getById(macroId) ?: return Result.success()
        if (!macro.enabled) return Result.success()

        // Tokens are filled in BEFORE Gemini sees the text, so the variation carries today's real
        // date/time/weekday and the model can't mangle a literal {token}. No sender: this is the
        // macro's own send, not a reply.
        val fixedBody = expandTemplate(macro.messageBody, LocalDateTime.now(), macro.name)

        val prefs = applicationContext.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_api_key", "").orEmpty()
        val model = prefs.getString("gemini_model", DEFAULT_GEMINI_MODEL)
            ?.ifBlank { DEFAULT_GEMINI_MODEL } ?: DEFAULT_GEMINI_MODEL
        // The per-macro instruction steers tone/style. The global Settings system prompt is NOT
        // used here — it is written for replying to incoming messages, not for rewriting.
        val generated: String? = if (apiKey.isBlank()) null else try {
            geminiGenerate(
                apiKey, buildVaryPrompt(macro.aiReplyInstruction), fixedBody, model,
                // Variations mirror the original's length; 300 gives headroom over the reply
                // pipeline's 150 for longer scheduled bodies without letting the model ramble.
                maxOutputTokens = 300
            ).trim().takeIf { it.isNotBlank() }
        } catch (cancelled: CancellationException) {
            // The system stopped this worker mid-call. Must NOT be treated as a Gemini failure:
            // the fallback path below would consume the one-shot claim (plain SharedPreferences,
            // it commits even under cancellation) and then abort at the first suspension — the
            // WorkManager re-run would dedupe on that claim and silently drop the day's send.
            throw cancelled
        } catch (t: Throwable) {
            null
        }
        // Same guard for a stop that lands after the call returned: claim only while still active.
        coroutineContext.ensureActive()

        if (generated == null) {
            // No key or Gemini unreachable → the fixed text goes out like a plain scheduled send,
            // approval mode or not: there is nothing AI-written to approve, and the day's claim is
            // already consumed. The override body skips MacroFirer's vary hand-off (no loop).
            if (!autoSendClaim.claim(macroId, party = "", eventId = eventId)) return Result.success()
            firer.fire(macroId, enforceOncePerDay = false, overrideBody = fixedBody)
            return Result.success()
        }

        when (macro.aiSendMode) {
            // No recipient: approval sends to the macro's own recipient list. Send/edit/discard
            // happen in the notification or the in-app dialog; the day's send stays consumed even
            // if the user discards — that is what discard means.
            AiSendMode.APPROVE -> notifications.notifyAiApproval(macro, null, generated)
            AiSendMode.AUTO -> {
                // Claim before sending so a WorkManager re-run after a mid-flight process death
                // (identical inputData → identical event id) can't send the variation twice.
                if (!autoSendClaim.claim(macroId, party = "", eventId = eventId)) return Result.success()
                // The normal result notification announces what was sent (it shows the body), and
                // a failure keeps its Retry action — no special AI notification needed.
                firer.fire(macroId, enforceOncePerDay = false, overrideBody = generated)
            }
        }
        return Result.success()
    }

    /** Required for the expedited request: on Android 8–11 WorkManager runs expedited work in a
     *  foreground service and asks for this first — the default implementation throws, which would
     *  fail the job before [doWork] and drop a scheduled send whose day-claim is already consumed. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val macroId = inputData.getString(KEY_MACRO_ID).orEmpty()
        val notifId = ("ai_fg_vary$macroId").hashCode() and 0x7FFFFFFF
        return ForegroundInfo(notifId, notifications.aiWorkingNotification("Writing message…"))
    }

    companion object {
        const val KEY_MACRO_ID = "macro_id"
        const val KEY_EVENT_ID = "event_id"

        /** Unique-work name per macro: yesterday's still-pending variation must be replaced, not
         *  stacked, when today's fire enqueues — two queued workers would double-send in AUTO. */
        fun uniqueName(macroId: String) = "ai_vary_$macroId"
    }
}
